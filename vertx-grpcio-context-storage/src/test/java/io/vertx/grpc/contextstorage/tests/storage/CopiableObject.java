package io.vertx.grpc.contextstorage.tests.storage;

public class CopiableObject {

  public final CopiableObject ref;

  public CopiableObject(CopiableObject ref) {
    this.ref = ref;
  }

  public CopiableObject() {
    this.ref = null;
  }
}
