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
package org.eclipse.ditto.signals.commands.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Aggregates all possible responses relating to a given {@link PolicyCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyCommandResponse<T extends PolicyCommandResponse> extends CommandResponse<T> {

    /**
     * Type Prefix of Policy command responses.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * PolicyCommandResponses are only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of PolicyCommandResponses.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * This class contains definitions for all specific fields of a {@code PolicyCommandResponse}'s JSON
     * representation.
     *
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the PolicyCommandResponse's policyId.
         */
        public static final JsonFieldDefinition JSON_POLICY_ID =
                JsonFactory.newFieldDefinition("policyId", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);
    }
}
