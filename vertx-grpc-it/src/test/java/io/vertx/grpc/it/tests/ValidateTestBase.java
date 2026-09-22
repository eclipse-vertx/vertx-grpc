package io.vertx.grpc.it.tests;

import com.google.protobuf.util.Timestamps;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.MethodCardinality;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.tests.GrpcTestBase;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.validation.protovalidate.Protovalidate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import vertx.weather.v1.GetWeatherRequest;
import vertx.weather.v1.GetWeatherResponse;
import vertx.weather.v1.WeatherGrpcService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(VertxUnitRunner.class)
public abstract class ValidateTestBase extends GrpcTestBase {

  protected static final ServiceName WEATHER = ServiceName.create("vertx.weather.v1", "Weather");

  protected static final ServiceMethod<GetWeatherResponse, GetWeatherRequest> GET_WEATHER = ServiceMethod.client(
    WEATHER,
    "GetWeather",
    MethodCardinality.UNARY,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(GetWeatherResponse.newBuilder()));

  protected static final ServiceMethod<GetWeatherResponse, GetWeatherRequest> GET_WEATHER_STREAM = ServiceMethod.client(
    WEATHER,
    "GetWeatherStream",
    MethodCardinality.CLIENT_STREAMING,
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(GetWeatherResponse.newBuilder()));

  protected static final class Outcome {

    final GrpcStatus status;
    final String message;

    Outcome(GrpcStatus status, String message) {
      this.status = status;
      this.message = message;
    }
  }

  protected final List<GetWeatherRequest> delivered = new CopyOnWriteArrayList<>();

  protected abstract void deploy(Service service);

  protected abstract <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(ServiceMethod<Resp, Req> method);

  @Override
  @Before
  public void setUp(TestContext should) {
    super.setUp(should);
    WeatherGrpcService service = new WeatherGrpcService() {
      @Override
      public Future<GetWeatherResponse> getWeather(GetWeatherRequest request) {
        delivered.add(request);
        return Future.succeededFuture(GetWeatherResponse.newBuilder().setSummary("sunny").build());
      }
      @Override
      public Future<GetWeatherResponse> getWeatherStream(ReadStream<GetWeatherRequest> request) {
        Promise<GetWeatherResponse> promise = Promise.promise();
        request.handler(delivered::add);
        request.endHandler(v -> promise.tryComplete(GetWeatherResponse.newBuilder().setSummary("sunny").build()));
        request.exceptionHandler(promise::tryFail);
        return promise.future();
      }
    };
    deploy(service.validator(Protovalidate.create()));
  }

  protected static GetWeatherRequest valid() {
    return request(0, 0, LocalDateTime.now().plusDays(1));
  }

  protected static GetWeatherRequest request(float latitude, float longitude, LocalDateTime forecast) {
    return GetWeatherRequest.newBuilder()
      .setLatitude(latitude)
      .setLongitude(longitude)
      .setForecastDate(Timestamps.fromMillis(forecast.toInstant(ZoneOffset.UTC).toEpochMilli()))
      .build();
  }

  private Future<Outcome> send(ServiceMethod<GetWeatherResponse, GetWeatherRequest> method, GetWeatherRequest... requests) {
    List<GetWeatherRequest> messages = Arrays.asList(requests);
    Promise<Outcome> promise = Promise.promise();
    request(method).onComplete(ar -> {
      if (ar.failed()) {
        promise.fail(ar.cause());
        return;
      }
      GrpcClientRequest<GetWeatherRequest, GetWeatherResponse> call = ar.result();
      call.response().onComplete(reply -> {
        if (reply.succeeded()) {
          GrpcClientResponse<GetWeatherRequest, GetWeatherResponse> response = reply.result();
          response.messageHandler(msg -> {
          });
          response.endHandler(v -> promise.tryComplete(new Outcome(response.status(), response.statusMessage())));
        } else {
          promise.tryComplete(outcomeOf(reply.cause()));
        }
      });
      for (int i = 0; i < messages.size() - 1; i++) {
        call.write(messages.get(i));
      }
      call.end(messages.get(messages.size() - 1)).onFailure(err -> promise.tryComplete(outcomeOf(err)));
    });
    return promise.future();
  }

  private static Outcome outcomeOf(Throwable err) {
    if (err instanceof InvalidStatusException) {
      return new Outcome(((InvalidStatusException) err).actualStatus(), null);
    }
    return new Outcome(GrpcStatus.UNKNOWN, String.valueOf(err));
  }

  private void assertRejected(TestContext should, Outcome outcome, String... expected) {
    should.assertEquals(GrpcStatus.INVALID_ARGUMENT, outcome.status);
    if (outcome.message != null) {
      for (String fragment : expected) {
        should.assertTrue(outcome.message.contains(fragment), outcome.message);
      }
    }
  }

  @Test
  public void testValidRequest(TestContext should) {
    send(GET_WEATHER, valid()).onComplete(should.asyncAssertSuccess(outcome -> {
      should.assertEquals(GrpcStatus.OK, outcome.status);
      should.assertEquals(1, delivered.size());
    }));
  }

  @Test
  public void testLatitudeOutOfRange(TestContext should) {
    send(GET_WEATHER, request(91, 0, LocalDateTime.now().plusDays(1))).onComplete(should.asyncAssertSuccess(outcome -> {
      assertRejected(should, outcome, "latitude");
      should.assertEquals(0, delivered.size());
    }));
  }

  @Test
  public void testLongitudeOutOfRange(TestContext should) {
    send(GET_WEATHER, request(0, -181, LocalDateTime.now().plusDays(1))).onComplete(should.asyncAssertSuccess(outcome -> {
      assertRejected(should, outcome, "longitude");
      should.assertEquals(0, delivered.size());
    }));
  }

  @Test
  public void testForecastDateBeyondSeventyTwoHours(TestContext should) {
    send(GET_WEATHER, request(0, 0, LocalDateTime.now().plusDays(7))).onComplete(should.asyncAssertSuccess(outcome -> {
      assertRejected(should, outcome, "72 hours");
      should.assertEquals(0, delivered.size());
    }));
  }

  @Test
  public void testEveryViolationIsReported(TestContext should) {
    send(GET_WEATHER, request(91, -181, LocalDateTime.now().plusDays(7))).onComplete(should.asyncAssertSuccess(outcome -> {
      assertRejected(should, outcome, "latitude", "longitude", "72 hours");
    }));
  }

  @Test
  public void testStreamIsValidatedPerMessage(TestContext should) {
    send(GET_WEATHER_STREAM, valid(), request(91, 0, LocalDateTime.now().plusDays(1)), valid())
      .onComplete(should.asyncAssertSuccess(outcome -> {
        assertRejected(should, outcome, "latitude");
        should.assertEquals(1, delivered.size());
      }));
  }
}
