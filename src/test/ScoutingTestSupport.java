package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.FieldAttachmentService;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Test-only factory for a ScoutingService on a temporary data folder. */
public final class ScoutingTestSupport {

    private ScoutingTestSupport() {
    }

    public static ScoutingService service(Path data, Clock clock) {
        ScoutingService service = new ScoutingService(new ScoutingStore(data, clock), season -> List.of(),
                new FarmProfile(), clock);
        service.setAttachments(new FieldAttachmentService(null, null, data.resolve("attachments").toString()));
        return service;
    }
}
