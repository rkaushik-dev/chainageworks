package io.github.rkaushikdev.chainageworks;

/** A referenced record does not exist in the store. */
public class NotFoundException extends CwException {

    public NotFoundException(String kind, String id) {
        super(ErrorCode.NOT_FOUND, "no " + kind + " with id " + id, Details.of("kind", kind, "id", id));
    }
}
