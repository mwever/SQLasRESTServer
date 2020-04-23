package ai.libs.sqlrest;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Mutable;

@Sources({ "file:conf/benchmark.properties" })
public interface IBenchmarkConfig extends Mutable {

	String K_BENCHMARK_TOKEN = "db.benchmark.token";

	String K_BENCHMARK_TABLE = "db.benchmark.table";

	String K_SERVICE_HOST = "db.benchmark.serviceHost";

	@Key(K_BENCHMARK_TOKEN)
	String getBenchmarkToken();

	@Key(K_BENCHMARK_TABLE)
    String getBenchmarkTable();

	@Key(K_SERVICE_HOST)
    @DefaultValue("http://localhost:8080")
    String getServiceHost();

}
