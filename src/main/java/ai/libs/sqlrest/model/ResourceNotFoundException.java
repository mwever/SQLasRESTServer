package ai.libs.sqlrest.model;

public class ResourceNotFoundException extends Exception{
	public ResourceNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
