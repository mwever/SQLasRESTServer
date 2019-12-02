package ai.libs.sqlrest;

import javax.persistence.Entity;

import lombok.Data;

@Data
@Entity
public class Experiment {

	private String name;
	private String token;

	public Experiment(final String name, final String token) {
		this.name = name;
		this.token = token;
	}

	public String getName() {
		return this.name;
	}

	public String getToken() {
		return this.token;
	}

}
