package io.vertx.grpc.common.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface GrpcClass {

  String packageName();

  String name();

}
