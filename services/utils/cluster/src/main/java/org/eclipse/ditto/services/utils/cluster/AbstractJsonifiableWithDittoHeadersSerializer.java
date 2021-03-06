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
package org.eclipse.ditto.services.utils.cluster;

import static java.util.Objects.requireNonNull;

import java.io.NotSerializableException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ExtendedActorSystem;
import akka.serialization.SerializerWithStringManifest;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Abstract {@link SerializerWithStringManifest} which handles serializing and deserializing {@link Jsonifiable}s {@link
 * WithDittoHeaders}.
 */
public abstract class AbstractJsonifiableWithDittoHeadersSerializer extends SerializerWithStringManifest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJsonifiableWithDittoHeadersSerializer.class);

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final JsonFieldDefinition JSON_DITTO_HEADERS =
            JsonFieldDefinition.newInstance("dittoHeaders", JsonObject.class);

    private static final JsonFieldDefinition JSON_PAYLOAD =
            JsonFieldDefinition.newInstance("payload", JsonObject.class);

    private final int identifier;
    private final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> mappingStrategies;
    private final Function<Object, String> manifestProvider;

    /**
     * Constructs a new {@code AbstractJsonifiableWithDittoHeadersSerializer} object.
     */
    protected AbstractJsonifiableWithDittoHeadersSerializer(final int identifier,
            final ExtendedActorSystem actorSystem,
            final Function<Object, String> manifestProvider) {
        this.identifier = identifier;

        // load via config the class implementing MappingStrategy:
        final String mappingStrategyClass =
                actorSystem.settings().config().getString("ditto.mapping-strategy.implementation");
        final ClassTag<MappingStrategy> tag = scala.reflect.ClassTag$.MODULE$.apply(MappingStrategy.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();
        final Try<MappingStrategy> mappingStrategy =
                actorSystem.dynamicAccess().createInstanceFor(mappingStrategyClass,
                        JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);

        mappingStrategies = new HashMap<>();
        mappingStrategies.putAll(requireNonNull(mappingStrategy.get().determineStrategy(), "mapping strategy"));
        this.manifestProvider = requireNonNull(manifestProvider, "manifest provider");
    }

    @Override
    public int identifier() {
        return identifier;
    }

    @Override
    public String manifest(final Object o) {
        return manifestProvider.apply(o);
    }

    @Override
    public byte[] toBinary(final Object object) {
        if (object instanceof Jsonifiable) {
            final DittoHeaders dittoHeaders;
            if (object instanceof WithDittoHeaders) {
                dittoHeaders = ((WithDittoHeaders) object).getDittoHeaders();
            } else {
                dittoHeaders = DittoHeaders.empty();
            }

            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();

            jsonObjectBuilder.set(JSON_DITTO_HEADERS, dittoHeaders.toJson());

            final JsonValue jsonValue;

            if (object instanceof Jsonifiable.WithPredicate) {
                final JsonSchemaVersion schemaVersion =
                        dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

                jsonValue = ((Jsonifiable.WithPredicate) object).toJson(schemaVersion, FieldType.regularOrSpecial());
            } else {
                jsonValue = ((Jsonifiable) object).toJson();
            }

            jsonObjectBuilder.set(JSON_PAYLOAD, jsonValue);

            return jsonObjectBuilder.build() //
                    .toString() //
                    .getBytes(UTF8_CHARSET);
        } else {
            LOG.error("Could not serialize class '{}' as it does not implement '{}'", object.getClass(),
                    Jsonifiable.WithPredicate.class);
            final String error = new NotSerializableException(object.getClass().getName()).getMessage();
            return error.getBytes(UTF8_CHARSET);
        }
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        final String json = new String(bytes, UTF8_CHARSET);
        try {
            return tryToCreateKnownJsonifiableFrom(manifest, json);
        } catch (final NotSerializableException e) {
            return e;
        }
    }

    private Jsonifiable tryToCreateKnownJsonifiableFrom(final String manifest, final String json)
            throws NotSerializableException {
        try {
            return createJsonifiableFrom(manifest, json);
        } catch (final DittoRuntimeException | JsonRuntimeException e) {
            LOG.error("Got {} during fromBinary(byte[],String) deserialization for manifest '{}' and JSON: '{}'",
                    e.getClass().getSimpleName(), manifest, json, e);
            throw new NotSerializableException(manifest);
        }
    }

    private Jsonifiable createJsonifiableFrom(final String manifest, final String json)
            throws NotSerializableException {
        final BiFunction<JsonObject, DittoHeaders, Jsonifiable> mappingFunction = mappingStrategies.get(manifest);
        if (null == mappingFunction) {
            LOG.warn("No strategy found to map manifest '{}' to a Jsonifiable.WithPredicate!", manifest);
            throw new NotSerializableException(manifest);
        }

        final JsonObject jsonObject = JsonFactory.newObject(json);

        final JsonObject payload = jsonObject.getValue(JSON_PAYLOAD)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());

        final DittoHeadersBuilder dittoHeadersBuilder = jsonObject.getValue(JSON_DITTO_HEADERS)
                .map(JsonValue::asObject)
                .map(DittoHeaders::newBuilder)
                .orElse(DittoHeaders.newBuilder());

        return mappingFunction.apply(payload, dittoHeadersBuilder.build());
    }

}
