package io.quarkiverse.qute.web.runtime;

final class ContentType {

    private static final String WILDCARD = "*";

    final String type;
    final String subtype;

    ContentType(String value) {
        int slash = value.indexOf('/');
        this.type = value.substring(0, slash);
        int semicolon = value.indexOf(';');
        this.subtype = semicolon != -1 ? value.substring(slash + 1, semicolon) : value.substring(slash + 1);
    }

    boolean matches(String otherType, String otherSubtype) {
        return (type.equals(otherType) || type.equals(WILDCARD) || otherType.equals(WILDCARD))
                && (subtype.equals(otherSubtype) || subtype.equals(WILDCARD) || otherSubtype.equals(WILDCARD));
    }

}