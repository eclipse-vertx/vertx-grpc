package io.vertx.grpc.eventbus.tests;

import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.tests.GrpcTestBase;
import io.vertx.grpc.common.tests.Empty;
import io.vertx.grpc.common.tests.Reply;
import io.vertx.grpc.common.tests.Request;
import io.vertx.grpc.common.tests.TestConstants;

public abstract class EventBusGrpcTestBase extends GrpcTestBase {

  static final ServiceMethod<Reply, Request> UNARY_CLIENT = ServiceMethod.client(
    TestConstants.TEST_SERVICE,
    "Unary",
    false,
    false,
    TestConstants.REQUEST_ENC,
    TestConstants.REPLY_DEC
  );

  static final ServiceMethod<Request, Reply> UNARY_SERVER = ServiceMethod.server(
    TestConstants.TEST_SERVICE,
    "Unary",
    false,
    false,
    TestConstants.REPLY_ENC,
    TestConstants.REQUEST_DEC
  );

  static final ServiceMethod<Empty, Reply> SOURCE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Source", false, true, TestConstants.REPLY_ENC, TestConstants.EMPTY_DEC);
  static final ServiceMethod<Request, Empty> SINK_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Sink", true, false, TestConstants.EMPTY_ENC, TestConstants.REQUEST_DEC);
  static final ServiceMethod<Request, Reply> PIPE_SERVER =
    ServiceMethod.server(TestConstants.TEST_SERVICE, "Pipe", true, true, TestConstants.REPLY_ENC, TestConstants.REQUEST_DEC);

  static final ServiceMethod<Reply, Empty> SOURCE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Source", false, true, TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);
  static final ServiceMethod<Empty, Request> SINK_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Sink", true, false, TestConstants.REQUEST_ENC, TestConstants.EMPTY_DEC);
  static final ServiceMethod<Reply, Request> PIPE_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Pipe", true, true, TestConstants.REQUEST_ENC, TestConstants.REPLY_DEC);

  static final ServiceMethod<Reply, Empty> UNKNOWN_CLIENT =
    ServiceMethod.client(TestConstants.TEST_SERVICE, "Unknown", false, true, TestConstants.EMPTY_ENC, TestConstants.REPLY_DEC);

}
