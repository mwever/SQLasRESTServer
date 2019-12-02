package ai.libs.sqlrest;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import lombok.Data;

@Data
@Entity
public class SQLQuery {

	private @Id @GeneratedValue Long id;
	private String token;
	private String query;

	public SQLQuery() {

	}

	public SQLQuery(final String token, final String query) {
		this.token = token;
		this.query = query;
	}

	public String getToken() {
		return this.token;
	}

	public String getQuery() {
		return this.query;
	}

}
