package ar.rindetuplata.mcp;

/**
 * A tool call the caller got wrong: an invalid or missing argument ({@link #INVALID_ARGUMENT}) or one that names
 * something that does not exist ({@link #NOT_FOUND}). It always names the argument, and the tool turns it into an
 * MCP tool error the model can read and fix (never a stack trace).
 */
final class ToolArgumentException extends RuntimeException {

	static final String INVALID_ARGUMENT = "invalid_argument";
	static final String NOT_FOUND = "not_found";

	private final String error;
	private final String argument;

	ToolArgumentException(String error, String argument, String detail) {
		super("Argument '" + argument + "': " + detail);
		this.error = error;
		this.argument = argument;
	}

	static ToolArgumentException invalid(String argument, String detail) {
		return new ToolArgumentException(INVALID_ARGUMENT, argument, detail);
	}

	static ToolArgumentException notFound(String argument, String detail) {
		return new ToolArgumentException(NOT_FOUND, argument, detail);
	}

	String error() {
		return error;
	}

	String argument() {
		return argument;
	}
}
