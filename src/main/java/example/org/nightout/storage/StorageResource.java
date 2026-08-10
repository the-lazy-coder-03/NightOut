package example.org.nightout.storage;

import org.springframework.core.io.Resource;

public record StorageResource(Resource resource, long contentLength) {
}
