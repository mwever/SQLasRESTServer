import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ai.libs.jaicore.basic.SQLAdapter;
import ai.libs.sqlrest.IServerConfig;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class BenchmarkTest {

	private static final IServerConfig CONFIG = ConfigFactory.create(IServerConfig.class);

	private static final boolean REST = true;

	private static final int MAX = 10000;

	public static void main(final String[] args) throws SQLException, IOException {
		Logger l = (Logger) LoggerFactory.getLogger("org.apache.http");
		l.setLevel(Level.OFF);

		String payloadBuilder = "";
		Random rand = new Random();
		for (int j = 0; j < 100; j++) {
			payloadBuilder += DigestUtils.sha512Hex(rand.nextDouble() + "");
		}
		final String payload = payloadBuilder;
		final AtomicInteger counter = new AtomicInteger(0);

		long startTime = System.currentTimeMillis();
		IntStream.range(0, MAX).parallel().forEach(i -> {
			try {
				counter.incrementAndGet();
				String randomString = DigestUtils.sha512Hex(new Random().nextDouble() + "");
				if (!REST) {
					sendSQLQueryViaAdapter(payload, randomString);
				} else {
					sendSQLQueryViaREST(payload, randomString);
				}

				if ((double) counter.get() % 1000 == 0) {
					System.out.println(((double) counter.get() / MAX * 100) + "%");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		long endTime = System.currentTimeMillis();

		System.out.println("Needed " + (endTime - startTime) + "ms for running the benchmark");

	}

	private static void sendSQLQueryViaAdapter(final String payload, final String randomString) throws SQLException {
		SQLAdapter adapter = new SQLAdapter(CONFIG.getDBHost(), CONFIG.getAdminDBUser(), CONFIG.getAdminDBPassword(), CONFIG.getAdminDBName());

		Map<String, String> data = new HashMap<>();
		data.put("random", randomString);
		data.put("payload", payload);
		adapter.insert("benchmark", data);

		adapter.close();

	}

	private static void sendSQLQueryViaREST(final String payload, final String randomString) throws MalformedURLException, IOException {
		CloseableHttpClient client = HttpClientBuilder.create().build();
		try {
			Map<String, String> data = new HashMap<>();
			data.put("random", randomString);
			data.put("payload", payload);
			String query = "INSERT INTO benchmark (random,payload) VALUES (\"" + randomString + "\",\"" + payload + "\")";

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = mapper.createObjectNode();
			root.set("token", root.textNode("bench"));
			root.set("query", root.textNode(query));
			String jsonPayload = mapper.writeValueAsString(root);

			StringEntity requestEntity = new StringEntity(jsonPayload, ContentType.APPLICATION_JSON);
			HttpPost post = new HttpPost("http://127.0.0.1:8080/insert");
			post.setEntity(requestEntity);
			client.execute(post);
		} finally {
			client.close();
		}
	}

}
