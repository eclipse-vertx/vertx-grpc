package io.vertx.benchmarks.transcoding;

import io.vertx.grpc.transcoding.impl.PercentEncoding;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class PercentEncodingBenchmark {

    private String simpleString;
    private String complexString;
    private String reservedCharsString;
    private String mixedString;

    @Setup
    public void setup() {
        simpleString = "HelloWorld";
        complexString = "Hello%20World%21";
        reservedCharsString = "path/to/resource?param=value&other=123";
        mixedString = "path%2Fto%2Fresource%3Fparam%3Dvalue%26other%3D123";
    }

    @Benchmark
    public void benchmarkUrlUnescapeSimpleString(Blackhole blackhole) {
        String result = PercentEncoding.urlUnescapeString(
            simpleString,
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS,
            true
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkUrlUnescapeComplexString(Blackhole blackhole) {
        String result = PercentEncoding.urlUnescapeString(
            complexString,
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS,
            true
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkUrlUnescapeReservedCharsString(Blackhole blackhole) {
        String result = PercentEncoding.urlUnescapeString(
            reservedCharsString,
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED,
            false
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkUrlUnescapeMixedString(Blackhole blackhole) {
        String result = PercentEncoding.urlUnescapeString(
            mixedString,
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED,
            false
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkUrlUnescapeExceptSlash(Blackhole blackhole) {
        String result = PercentEncoding.urlUnescapeString(
            mixedString,
            PercentEncoding.UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_SLASH,
            false
        );
        blackhole.consume(result);
    }
}
