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
package org.eclipse.ditto.signals.commands.policies.modify;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies a {@link Subject} of a {@link org.eclipse.ditto.model.policies.PolicyEntry}'s {@link
 * org.eclipse.ditto.model.policies.Subjects}.
 */
@Immutable
public final class ModifySubject extends AbstractCommand<ModifySubject> implements PolicyModifyCommand<ModifySubject> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifySubject";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition JSON_LABEL =
            JsonFactory.newFieldDefinition("label", String.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition JSON_SUBJECT_ID =
            JsonFactory.newFieldDefinition("subjectId", String.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition JSON_SUBJECT =
            JsonFactory.newFieldDefinition("subject", JsonObject.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    private final Subject subject;

    private ModifySubject(final String policyId, final Label label, final Subject subject,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.label = label;
        this.subject = subject;
    }

    /**
     * Creates a command for modifying {@code Subject} of a {@code Policy}'s {@code PolicyEntry}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param subject the Subject to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubject of(final String policyId, final Label label, final Subject subject,
            final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(label, "The Label must not be null!");
        Objects.requireNonNull(subject, "The Subject must not be null!");
        return new ModifySubject(policyId, label, subject, dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code Subject} of a {@code Policy}'s {@code PolicyEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifySubject fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code Subject} of a {@code Policy}'s {@code PolicyEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifySubject fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifySubject>(TYPE, jsonObject).deserialize(jsonObjectReader -> {
            final String policyId = jsonObjectReader.get(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
            final String stringLabel = jsonObjectReader.get(JSON_LABEL);
            final Label label = PoliciesModelFactory.newLabel(stringLabel);
            final String subjectId = jsonObjectReader.get(JSON_SUBJECT_ID);
            final JsonObject subjectJsonObject = jsonObjectReader.get(JSON_SUBJECT);
            final Subject subject = PoliciesModelFactory.newSubject(subjectId, subjectJsonObject);

            return of(policyId, label, subject, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Subject} to modify.
     *
     * @return the Label of the PolicyEntry whose Subject to modify.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the {@code Subject} to modify.
     *
     * @return the Subject to modify.
     */
    public Subject getSubject() {
        return subject;
    }

    /**
     * Returns the identifier of the {@code Policy} whose {@code PolicyEntry} to modify.
     *
     * @return the identifier of the Policy whose PolicyEntry to modify.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(subject.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subject.getId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subject.getId().toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT, subject.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ModifySubject setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, subject, dittoHeaders);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof ModifySubject);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ModifySubject that = (ModifySubject) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(subject, that.subject) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", subject=" + subject + "]";
    }

}
