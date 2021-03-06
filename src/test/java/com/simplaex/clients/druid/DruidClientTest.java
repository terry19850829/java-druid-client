package com.simplaex.clients.druid;


import io.druid.query.Druids;
import io.druid.query.Result;
import io.druid.query.metadata.metadata.AllColumnIncluderator;
import io.druid.query.metadata.metadata.SegmentAnalysis;
import io.druid.query.metadata.metadata.SegmentMetadataQuery;
import io.druid.query.select.EventHolder;
import io.druid.query.select.PagingSpec;
import io.druid.query.select.SelectQuery;
import io.druid.query.select.SelectResultValue;
import org.bouncycastle.util.io.Streams;
import org.joda.time.Interval;
import org.junit.*;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class DruidClientTest {

  private static ClientAndServer mockServer;

  @BeforeClass
  public static void beforeAll() {
    mockServer = new ClientAndServer();
  }

  @AfterClass
  public static void afterAll() {
    mockServer.stop();
  }

  @After
  public void afterEach() {
    mockServer.reset();
  }

  private String load(final String resourceName) throws IOException {
    final InputStream responseInputStream =
        getClass().getResourceAsStream(resourceName);

    final byte[] responseBytes =
        Streams.readAll(responseInputStream);

    return new String(responseBytes, StandardCharsets.UTF_8);
  }

  private void setResponse(final String response) {
    mockServer
        .when(
            HttpRequest
                .request("/druid/v2/")
                .withMethod("POST")
        )
        .respond(
            HttpResponse
                .response(response)
        );
  }

  @Test
  public void shouldExecuteASegmentMetadataQuery() throws IOException {

    setResponse(load("segment_metadata_query_response.json"));

    final DruidClient client =
        DruidClient.create("localhost", mockServer.getPort());

    final SegmentMetadataQuery query = new Druids.SegmentMetadataQueryBuilder()
        .dataSource("player_explorer_s3")
        .analysisTypes(EnumSet.allOf(SegmentMetadataQuery.AnalysisType.class))
        .toInclude(new AllColumnIncluderator())
        .build();

    final DruidResult<SegmentAnalysis> druidResult = client.run(query);

    final List<SegmentAnalysis> result = druidResult.toList();

    Assert.assertEquals(
        "Number of results should be 14",
        14,
        result.size()
    );

    final Set<String> columns = result.get(0).getColumns().keySet();

    final String[] columnsArray = columns.toArray(new String[columns.size()]);
    Arrays.sort(columnsArray);

    final String[] expectedColumnsArray = new String[]{
        "__time",
        "acquisitionSource",
        "activeDaysBucket",
        "activeDaysSum",
        "age",
        "bundles",
        "count",
        "countryCode",
        "deviceOs",
        "deviceType",
        "genres",
        "otherApps",
        "paidAmountBucket",
        "paidAmountSum",
        "paymentCount",
        "paymentsBucket",
        "regDate",
        "sessionCount"
    };
    Arrays.sort(expectedColumnsArray);

    Assert.assertArrayEquals(
        "All columns should be included in the output",
        expectedColumnsArray,
        columnsArray
    );
  }

  @Test
  public void shouldExecuteASelectQuery() throws IOException {

    setResponse(load("select_query_response.json"));

    final DruidClient client =
        DruidClient.create("localhost", mockServer.getPort());

    final long from = Instant.parse("2017-08-01T00:00:00Z").toEpochMilli();
    final long to = Instant.parse("2017-08-20T00:00:00Z").toEpochMilli();

    final SelectQuery query = new Druids.SelectQueryBuilder()
        .dataSource("player_explorer_s3")
        .dimensions(Collections.singletonList("deviceType"))
        .intervals(Collections.singletonList(new Interval(from, to)))
        .pagingSpec(new PagingSpec(Collections.emptyMap(), 100))
        .build();

    final DruidResult<Result<SelectResultValue>> result = client.run(query);
    final List<Result<SelectResultValue>> resultList = result.toList();

    Assert.assertEquals(
        "Number of results should be 1",
        1,
        resultList.size()
    );

    final Result<SelectResultValue> firstResult = resultList.get(0);
    final SelectResultValue selectResult = firstResult.getValue();

    final List<EventHolder> events = selectResult.getEvents();

    Assert.assertEquals(
        "should have 100 events",
        100,
        events.size()
    );

    Assert.assertEquals(
        "the last event should have a sessionCount of 138",
        events.get(99).getEvent().get("sessionCount"),
        138
    );
  }

}
