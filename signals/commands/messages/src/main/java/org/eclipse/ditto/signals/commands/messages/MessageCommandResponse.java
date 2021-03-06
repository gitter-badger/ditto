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
package org.eclipse.ditto.signals.commands.messages;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Base interface for all response messages to things and features.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the MessageCommandResponse.
 */
public interface MessageCommandResponse<T, C extends MessageCommandResponse>
        extends CommandResponse<C>, WithId, WithThingId {

    /**
     * Type Prefix of Message commands.
     */
    String TYPE_PREFIX = "messages." + TYPE_QUALIFIER + ":";

    /**
     * Retrieves the Message to be delivered.
     *
     * @return the Message to be delivered.
     */
    Message<T> getMessage();

    /**
     * Retrieves the type of the message to be delivered. This will be used as routingKey for delivering the message
     * over AMQP.
     *
     * @return the type of the message to be delivered
     */
    default String getMessageType() {
        return getName();
    }

    @Override
    default String getId() {
        return getThingId();
    }

    @Override
    C setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonPointer getResourcePath() {
        final Message<?> message = getMessage();
        final String box = message.getDirection() == MessageDirection.TO ? MessageCommand.INBOX_PREFIX : MessageCommand.OUTBOX_PREFIX;
        final JsonPointer pathSuffix = JsonPointer.empty()
                .addLeaf(JsonKey.of(box))
                .addLeaf(JsonKey.of(MessageCommand.MESSAGES_PREFIX))
                .addLeaf(JsonKey.of(message.getSubject()));

        final JsonPointer path = message.getFeatureId().map(fId -> JsonPointer.empty()
                .addLeaf(JsonKey.of(MessageCommand.FEATURES_PREFIX))
                .addLeaf(JsonKey.of(fId)))
                .orElse(JsonPointer.empty());
        return path.append(pathSuffix);
    }

    /**
     * This class contains definitions for all specific fields of a {@code MessageCommandResponse}'s JSON
     * representation.
     *
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the MessageCommandResponse's thingId.
         */
        public static final JsonFieldDefinition JSON_THING_ID =
                JsonFactory.newFieldDefinition("thingId", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message.
         */
        public static JsonFieldDefinition JSON_MESSAGE =
                JsonFactory.newFieldDefinition("message", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message headers.
         */
        public static JsonFieldDefinition JSON_MESSAGE_HEADERS =
                JsonFactory.newFieldDefinition("headers", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message payload.
         */
        public static JsonFieldDefinition JSON_MESSAGE_PAYLOAD =
                JsonFactory.newFieldDefinition("payload", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);
    }
}
