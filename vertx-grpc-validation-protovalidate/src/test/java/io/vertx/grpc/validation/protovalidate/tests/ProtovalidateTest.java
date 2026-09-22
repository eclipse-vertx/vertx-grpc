package io.vertx.grpc.validation.protovalidate.tests;

import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcMessageValidator;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.GrpcValidationException;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.tests.GrpcTestBase;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.validation.protovalidate.Protovalidate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertx.weather.v1.GetWeatherRequest;
import vertx.weather.v1.GetWeatherResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProtovalidateTest extends GrpcTestBase {

  private static final ServiceName WEATHER = ServiceName.create("vertx.weather.v1", "Weather");

  private static final ServiceMethod<GetWeatherRequest, GetWeatherResponse> GET_WEATHER = ServiceMethod.server(
    WEATHER,
    "GetWeather",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(GetWeatherRequest.newBuilder()));

  private static final ServiceMethod<GetWeatherResponse, GetWeatherRequest> GET_WEATHER_CLIENT = ServiceMethod.client(
    WEATHER,
    "GetWeather",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(GetWeatherResponse.newBuilder()));

  private static GetWeatherRequest valid() {
    return request(0, 0, LocalDateTime.now().plusDays(1));
  }

  private static GetWeatherRequest request(float latitude, float longitude, LocalDateTime forecast) {
    return GetWeatherRequest.newBuilder()
      .setLatitude(latitude)
      .setLongitude(longitude)
      .setForecastDate(Timestamps.fromMillis(forecast.toInstant(ZoneOffset.UTC).toEpochMilli()))
      .build();
  }

  private final AtomicInteger delivered = new AtomicInteger();
  private GrpcClient client;

  @Override
  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    GrpcServer server = GrpcServer.server(vertx).callHandler(GET_WEATHER, call -> {
      call.handler(request -> {
        delivered.incrementAndGet();
        call.response().end(GetWeatherResponse.newBuilder().setSummary("sunny").build());
      });
    }, Protovalidate.create());
    vertx
      .createHttpServer()
      .requestHandler(server)
      .listen(port)
      .await();
    client = GrpcClient.client(vertx);
  }

  @Override
  @After
  public void tearDown(TestContext should) {
    if (client != null) {
      client.close().await();
      client = null;
    }
    super.tearDown(should);
  }

  private Future<GrpcClientResponse<GetWeatherRequest, GetWeatherResponse>> call(GetWeatherRequest request) {
    Promise<GrpcClientResponse<GetWeatherRequest, GetWeatherResponse>> promise = Promise.promise();
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), GET_WEATHER_CLIENT).onComplete(ar -> {
      if (ar.failed()) {
        promise.fail(ar.cause());
        return;
      }
      GrpcClientRequest<GetWeatherRequest, GetWeatherResponse> call = ar.result();
      call.end(request);
      call.response().onComplete(reply -> {
        if (reply.failed()) {
          promise.fail(reply.cause());
          return;
        }
        GrpcClientResponse<GetWeatherRequest, GetWeatherResponse> response = reply.result();
        response.messageHandler(msg -> {
        });
        response.endHandler(v -> promise.complete(response));
      });
    });
    return promise.future();
  }

  @Test
  public void testValidRequest(TestContext should) {
    call(valid()).onComplete(should.asyncAssertSuccess(response -> {
      should.assertEquals(GrpcStatus.OK, response.status());
      should.assertEquals(1, delivered.get());
      response.last().onComplete(should.asyncAssertSuccess(reply ->
        should.assertEquals("sunny", reply.getSummary())));
    }));
  }

  @Test
  public void testLatitudeOutOfRange(TestContext should) {
    call(request(91, 0, LocalDateTime.now().plusDays(1))).onComplete(should.asyncAssertSuccess(response -> {
      should.assertEquals(GrpcStatus.INVALID_ARGUMENT, response.status());
      should.assertTrue(response.statusMessage().contains("latitude"), response.statusMessage());
      should.assertEquals(0, delivered.get());
    }));
  }

  @Test
  public void testLongitudeOutOfRange(TestContext should) {
    call(request(0, -181, LocalDateTime.now().plusDays(1))).onComplete(should.asyncAssertSuccess(response -> {
      should.assertEquals(GrpcStatus.INVALID_ARGUMENT, response.status());
      should.assertTrue(response.statusMessage().contains("longitude"), response.statusMessage());
      should.assertEquals(0, delivered.get());
    }));
  }

  @Test
  public void testForecastDateBeyondSeventyTwoHours(TestContext should) {
    call(request(0, 0, LocalDateTime.now().plusDays(7))).onComplete(should.asyncAssertSuccess(response -> {
      should.assertEquals(GrpcStatus.INVALID_ARGUMENT, response.status());
      should.assertTrue(response.statusMessage().contains("72 hours"), response.statusMessage());
      should.assertEquals(0, delivered.get());
    }));
  }

  @Test
  public void testViolationsAreReported() {
    GrpcMessageValidator<Message> validator = Protovalidate.create();
    try {
      validator.validate(request(91, -181, LocalDateTime.now().plusDays(7)));
      fail();
    } catch (GrpcValidationException e) {
      assertEquals(3, e.violations().size());
      boolean latitude = false;
      boolean longitude = false;
      boolean forecast = false;
      for (GrpcValidationException.Violation violation : e.violations()) {
        if ("latitude".equals(violation.field())) {
          latitude = true;
          assertEquals("float.gte_lte", violation.rule());
        } else if ("longitude".equals(violation.field())) {
          longitude = true;
          assertEquals("float.gte_lte", violation.rule());
        } else if ("forecast_date".equals(violation.field())) {
          forecast = true;
          assertEquals("forecast_date.within_72_hours", violation.rule());
        }
        assertTrue(violation.message() != null && !violation.message().isEmpty());
      }
      assertTrue(latitude);
      assertTrue(longitude);
      assertTrue(forecast);
    }
  }

  @Test
  public void testEagerCompilation() throws Exception {
    Validator eager = ValidatorFactory
      .newBuilder()
      .buildWithDescriptors(Collections.singletonList(GET_WEATHER.decoder().messageDescriptor()), true);
    GrpcMessageValidator<Message> validator = Protovalidate.create(eager);
    validator.validate(valid());
    try {
      validator.validate(request(91, 0, LocalDateTime.now().plusDays(1)));
      fail();
    } catch (GrpcValidationException e) {
      assertFalse(e.violations().isEmpty());
    }
  }
}
