package ai.libs.sqlrest.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="No such Token")
public class ExperimentTokenNotFound extends RuntimeException {

    public ExperimentTokenNotFound(String token) {
        super(String.format("The supplied token was not found in the experiments table in the admin database: %s", token));
    }

}
