/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.devops;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;

/**
 * A {@link JsonParsableRegistry} aware of all {@link DevOpsCommandResponse}s.
 */
@Immutable
public final class DevOpsCommandResponseRegistry extends AbstractJsonParsableRegistry<DevOpsCommandResponse> implements
        JsonParsableRegistry<DevOpsCommandResponse> {

    private DevOpsCommandResponseRegistry(final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code DevOpsCommandRegistry}.
     *
     * @return the registry.
     */
    public static DevOpsCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(ChangeLogLevelResponse.TYPE, ChangeLogLevelResponse::fromJson);
        parseStrategies.put(RetrieveLoggerConfigResponse.TYPE, RetrieveLoggerConfigResponse::fromJson);
        parseStrategies.put(RetrieveStatisticsResponse.TYPE, RetrieveStatisticsResponse::fromJson);

        return new DevOpsCommandResponseRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        final Optional<String> typeOpt =
                jsonObject.getValue(DevOpsCommandResponse.JsonFields.TYPE).map(JsonValue::asString);
        return typeOpt.orElseThrow(() -> JsonMissingFieldException.newBuilder() // fail if "type" is not present
                .fieldName(DevOpsCommandResponse.JsonFields.TYPE.getPointer().toString()).build());
    }
}
