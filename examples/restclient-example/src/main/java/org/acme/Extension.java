package org.acme;

import java.util.List;

public record Extension(String id, String name, List<String> keywords) {
}
