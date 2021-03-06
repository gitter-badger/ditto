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
package org.eclipse.ditto.services.things.persistence.actors;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.model.things.ThingsModelFactory.newAclEntry;
import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permissions;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

/**
 * Unit test for the {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorTest extends PersistenceActorTestBase {

    private static final AuthorizationSubject AUTHORIZATION_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(
                    SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, "testuser").toString());
    private static final Permissions PERMISSIONS = Thing.MIN_REQUIRED_PERMISSIONS;

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    /** */
    @Before
    public void setUp() {
        setup(ConfigFactory.empty());
    }

    /** */
    @Test
    public void unavailableExpectedIfPersistenceActorTerminates() throws Exception {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElse(null);

                final ActorRef underTest = createSupervisorActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // retrieve created thing
                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersMockV2);
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(RetrieveThingResponse.of(thingId, thing.toJson(), dittoHeadersMockV2));

                // terminate thing persistence actor
                final String thingActorPath = String.format("akka://AkkaTestSystem/user/%s/pa", thingId);
                final ActorSelection thingActorSelection = actorSystem.actorSelection(thingActorPath);
                final Future<ActorRef> thingActorFuture =
                        thingActorSelection.resolveOne(Duration.create(5, TimeUnit.SECONDS));
                Await.result(thingActorFuture, Duration.create(6, TimeUnit.SECONDS));
                final ActorRef thingActor = watch(thingActorFuture.value().get().get());

                watch(thingActor);
                thingActor.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(thingActor);

                // retrieve unavailable thing
                underTest.tell(retrieveThing, getRef());
                expectMsgClass(ThingUnavailableException.class);
            }
        };
    }

    /** */
    @Test
    public void tryToModifyFeaturePropertyAndReceiveCorrectErrorCode() {
        final String thingId = "org.eclipse.ditto:myThing";
        final Thing thing = Thing.newBuilder() //
                .setId(thingId) //
                .setFeatures(JsonFactory.newObject()) //
                .build();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV1);

        final String featureId = "myFeature";
        final JsonPointer jsonPointer = JsonPointer.of("/state");
        final JsonValue jsonValue = JsonFactory.newValue("on");
        final ModifyFeatureProperty modifyFeatureProperty =
                ModifyFeatureProperty.of(thingId, featureId, jsonPointer, jsonValue, dittoHeadersMockV1);

        final FeatureNotAccessibleException featureNotAccessibleException =
                FeatureNotAccessibleException.newBuilder(thingId, featureId)
                        .dittoHeaders(dittoHeadersMockV1)
                        .build();

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingId);

                underTest.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);

                underTest.tell(modifyFeatureProperty, getRef());
                final Object actual = receiveOne(scala.concurrent.duration.Duration.apply(1, TimeUnit.SECONDS));
                assertThat(actual).isInstanceOf(DittoRuntimeException.class);
                assertThat(((DittoRuntimeException) actual).getErrorCode()).isEqualTo(
                        featureNotAccessibleException.getErrorCode());
            }
        };
    }

    /** */
    @Test
    public void tryToRetrieveThingWhichWasNotYetCreated() {
        final String thingId = "test.ns:23420815";
        final ThingCommand retrieveThingCommand = RetrieveThing.of(thingId, dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thingId);
                thingPersistenceActor.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    /**
     * The ThingPersistenceActor is created with a Thing ID. Any command it receives which belongs to a Thing with a
     * different ID should lead to an exception as the command was obviously sent to the wrong ThingPersistenceActor.
     */
    @Test
    public void tryToCreateThingWithDifferentThingId() {
        final String thingIdOfActor = "test.ns:23420815";
        final Thing thing = createThingV2WithRandomId();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);

        final Props props = ThingPersistenceActor.props(thingIdOfActor, pubSubMediator, thingCacheFacade);
        final TestActorRef<ThingPersistenceActor> underTest = TestActorRef.create(actorSystem, props);
        final ThingPersistenceActor thingPersistenceActor = underTest.underlyingActor();
        final PartialFunction<Object, BoxedUnit> receiveCommand = thingPersistenceActor.receiveCommand();

        try {
            receiveCommand.apply(createThing);
            fail("Expected IllegalArgumentException to be thrown.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** */
    @Test
    public void createThingV2() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);
            }
        };
    }

    /** */
    @Test
    public void modifyThingV2() {
        final Thing thing = createThingV2WithRandomId();

        final Thing modifiedThing = thing.setAttribute(JsonFactory.newPointer("foo/bar"), JsonFactory.newValue("baz"));
        final ModifyThing modifyThingCommand =
                ModifyThing.of(thing.getId().orElse(null), modifiedThing, null, dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(ModifyThingResponse.modified(thing.getId().orElse(null), dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void retrieveThingV2() {
        final Thing thing = createThingV2WithRandomId();
        final ThingCommand retrieveThingCommand = RetrieveThing.of(thing.getId().orElse(null), dittoHeadersMockV2);
        final RetrieveThingResponse expectedResponse =
                RetrieveThingResponse.of(thing.getId().orElse(null), thing.toJson(),
                        retrieveThingCommand.getDittoHeaders());

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void retrieveThingsWithoutThingIdOfActor() {
        final Thing thing = createThingV2WithRandomId();

        final RetrieveThings retrieveThingsCommand = RetrieveThings.getBuilder("foo", "bar").build();

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingsCommand, getRef());
                expectNoMsg();
            }
        };
    }

    /** */
    @Test
    public void deleteThingV1() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(thing.getId().orElse(null), dittoHeadersMockV1);
                thingPersistenceActor.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thing.getId().orElse(null), dittoHeadersMockV1));
            }
        };
    }

    /** */
    @Test
    public void deleteThingV2() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(thing.getId().orElse(null), dittoHeadersMockV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thing.getId().orElse(null), dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void modifyFeatures() {
        new JavaTestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);

                final String thingId = ":myThing";
                final Feature smokeDetector = ThingsModelFactory.newFeature("smokeDetector");
                final Feature fireExtinguisher = ThingsModelFactory.newFeature("fireExtinguisher");
                final Thing thing = ThingsModelFactory.newThingBuilder() //
                        .setId(thingId) //
                        .setFeature(smokeDetector) //
                        .build();
                final Features featuresToModify = ThingsModelFactory.newFeatures(fireExtinguisher);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyFeatures modifyFeatures =
                        ModifyFeatures.of(thingId, featuresToModify, headersMockWithOtherAuth);
                underTest.tell(modifyFeatures, getRef());
                expectMsgEquals(ModifyFeaturesResponse.modified(thing.getId().orElse(null), headersMockWithOtherAuth));

                final RetrieveFeatures retrieveFeatures = RetrieveFeatures.of(thingId, headersMockWithOtherAuth);
                final ThingQueryCommandResponse expectedResponse =
                        RetrieveFeaturesResponse.of(thing.getId().orElse(null), featuresToModify.toJson(),
                                headersMockWithOtherAuth);
                underTest.tell(retrieveFeatures, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void modifyAttributes() {
        new JavaTestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);

                final String thingId = ":myThing";

                final JsonPointer fooPointer = JsonFactory.newPointer("foo");
                final JsonValue fooValue = JsonFactory.newValue("bar");
                final JsonPointer bazPointer = JsonFactory.newPointer("baz");
                final JsonValue bazValue = JsonFactory.newValue(42);

                final Thing thing = ThingsModelFactory.newThingBuilder() //
                        .setId(thingId) //
                        .setAttribute(fooPointer, fooValue) //
                        .build();
                final Attributes attributesToModify =
                        ThingsModelFactory.newAttributesBuilder().set(bazPointer, bazValue).build();

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyAttributes modifyAttributes =
                        ModifyAttributes.of(thingId, attributesToModify, headersMockWithOtherAuth);
                underTest.tell(modifyAttributes, getRef());
                expectMsgEquals(
                        ModifyAttributesResponse.modified(thing.getId().orElse(null), headersMockWithOtherAuth));

                final RetrieveAttributes retrieveAttributes = RetrieveAttributes.of(thingId, headersMockWithOtherAuth);
                final ThingQueryCommandResponse expectedResponse = RetrieveAttributesResponse
                        .of(thing.getId().orElse(null), attributesToModify.toJson(JsonSchemaVersion.LATEST),
                                headersMockWithOtherAuth);
                underTest.tell(retrieveAttributes, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void modifyAttribute() {
        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set("isValid", false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder() //
                .setAttributes(ThingsModelFactory.newAttributes(attributes)) //
                .setGeneratedId() //
                .build();

        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue newAttributeValue = JsonFactory.newValue(true);

        final String thingId = thing.getId().orElse(null);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Modify attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        ModifyAttribute.of(thingId, attributeKey, newAttributeValue, dittoHeadersMockV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(ModifyAttributeResponse.modified(thingId, attributeKey, dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void retrieveAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue attributeValue = JsonFactory.newValue(false);

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, attributeValue).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder() //
                .setAttributes(ThingsModelFactory.newAttributes(attributes)) //
                .setGeneratedId() //
                .build();

        final String thingId = thing.getId().orElse(null);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Retrieve attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        RetrieveAttribute.of(thingId, attributeKey, dittoHeadersMockV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgClass(RetrieveAttributeResponse.class);
            }
        };
    }

    /** */
    @Test
    public void deleteAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder() //
                .setAttributes(ThingsModelFactory.newAttributes(attributes)) //
                .setGeneratedId() //
                .build();

        final String thingId = thing.getId().orElse(null);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Delete attribute as authorized subject.
                final ThingCommand authorizedCommand = DeleteAttribute.of(thingId, attributeKey, dittoHeadersMockV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(DeleteAttributeResponse.of(thingId, attributeKey, dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void tryToRetrieveThingAfterDeletion() {
        final Thing thing = createThingV2WithRandomId();
        final DeleteThing deleteThingCommand = DeleteThing.of(thing.getId().orElse(null), dittoHeadersMockV2);
        final RetrieveThing retrieveThingCommand = RetrieveThing.of(thing.getId().orElse(null), dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(deleteThingCommand, getRef());
                expectMsgEquals(DeleteThingResponse.of(thing.getId().orElse(null), dittoHeadersMockV2));

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    /** */
    @Test
    public void recoverThingCreated() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElse(null);

                ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = createPersistenceActorFor(thing);

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersMockV2);
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                final Thing thingAsPersisted = retrieveThingResponse.getThing();
                assertThat(thingAsPersisted.getId()).isEqualTo(thing.getId());
                assertThat(thingAsPersisted.getAttributes()).isEqualTo(thing.getAttributes());
                assertThat(thingAsPersisted.getFeatures()).isEqualTo(thing.getFeatures());
            }
        };
    }

    /** */
    @Test
    public void recoverThingDeleted() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElse(null);

                ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersMockV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersMockV2));

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = createPersistenceActorFor(thing);

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersMockV2);
                underTest.tell(retrieveThing, getRef());

                // A deleted Thing cannot be retrieved anymore.
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    /** */
    @Test
    public void recoverAclModified() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thingV1 = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thingV1);

                final CreateThing createThing = CreateThing.of(thingV1, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                final AccessControlList acl = ThingsModelFactory.newAcl(newAclEntry(AUTHORIZED_SUBJECT, PERMISSIONS),
                                newAclEntry(AUTHORIZATION_SUBJECT, PERMISSIONS));
                final ModifyAcl modifyAcl = ModifyAcl.of(thingV1.getId().orElse(null), acl, dittoHeadersMockV1);
                thingPersistenceActor.tell(modifyAcl, getRef());
                expectMsgEquals(ModifyAclResponse.modified(thingV1.getId().orElse(null), acl, dittoHeadersMockV1));

                // restart
                final ActorRef thingPersistenceActorRecovered = createPersistenceActorFor(thingV1);

                final Thing thingWithUpdatedAcl = thingV1.setAccessControlList(acl);
                final ThingQueryCommandResponse response =
                        RetrieveThingResponse.of(thingWithUpdatedAcl.getId().orElse(null),
                                thingWithUpdatedAcl.toJson(JsonSchemaVersion.V_1),
                                dittoHeadersMockV1);
                final RetrieveThing retrieveThing =
                        RetrieveThing.of(thingWithUpdatedAcl.getId().orElse(null), dittoHeadersMockV1);
                thingPersistenceActorRecovered.tell(retrieveThing, getRef());
                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void recoverAclEntryModified() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thingV1 = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thingV1);

                final CreateThing createThing = CreateThing.of(thingV1, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                final AclEntry aclEntry = newAclEntry(AUTHORIZATION_SUBJECT, PERMISSIONS);
                final ModifyAclEntry modifyAclEntry =
                        ModifyAclEntry.of(thingV1.getId().orElse(null), aclEntry, dittoHeadersMockV1);
                thingPersistenceActor.tell(modifyAclEntry, getRef());
                expectMsgEquals(
                        ModifyAclEntryResponse.created(thingV1.getId().orElse(null), aclEntry, dittoHeadersMockV1));

                // restart
                final ActorRef thingPersistenceActorRecovered = createPersistenceActorFor(thingV1);

                final Thing thingWithUpdatedAclEntry = thingV1.setAclEntry(aclEntry);
                final ThingQueryCommandResponse response =
                        RetrieveThingResponse.of(thingWithUpdatedAclEntry.getId().orElse(null),
                                thingWithUpdatedAclEntry.toJson(JsonSchemaVersion.V_1),
                                dittoHeadersMockV1);
                final RetrieveThing retrieveThing =
                        RetrieveThing.of(thingWithUpdatedAclEntry.getId().orElse(null), dittoHeadersMockV1);
                thingPersistenceActorRecovered.tell(retrieveThing, getRef());
                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void recoverAclEntryDeleted() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thingV1 = createThingV1WithRandomId();
                final AclEntry aclEntry = newAclEntry(AUTHORIZATION_SUBJECT, PERMISSIONS);
                final Thing thingWithUpdatedAclEntry = thingV1.setAclEntry(aclEntry);

                final ActorRef underTest = createPersistenceActorFor(thingWithUpdatedAclEntry);
                final CreateThing createThing = CreateThing.of(thingWithUpdatedAclEntry, null, dittoHeadersMockV1);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                final DeleteAclEntry deleteAclEntry = DeleteAclEntry
                        .of(thingWithUpdatedAclEntry.getId().orElse(null), aclEntry.getAuthorizationSubject(),
                                dittoHeadersMockV1);
                underTest.tell(deleteAclEntry, getRef());
                expectMsgEquals(
                        DeleteAclEntryResponse.of(thingWithUpdatedAclEntry.getId().orElse(null), AUTHORIZATION_SUBJECT,
                                dittoHeadersMockV1));

                // restart
                final ActorRef thingPersistenceActorRecovered = createPersistenceActorFor(thingV1);

                final ThingQueryCommandResponse response = RetrieveThingResponse.of(thingV1.getId().orElse(null),
                        thingWithUpdatedAclEntry.removeAllPermissionsOf(aclEntry.getAuthorizationSubject())
                                .toJson(JsonSchemaVersion.V_1), dittoHeadersMockV1);
                final RetrieveThing retrieveThing =
                        RetrieveThing.of(thingV1.getId().orElse(null), dittoHeadersMockV1);
                thingPersistenceActorRecovered.tell(retrieveThing, getRef());
                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void ensureSequenceNumberCorrectness() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElse(null);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersMockV2);

                underTest.tell(modifyThing, getRef());

                expectMsgEquals(ModifyThingResponse.modified(thingId, modifyThing.getDittoHeaders()));

                // retrieve the thing's sequence number
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.toString(), JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify) //
                        .setRevision(versionExpected) //
                        .build();
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersMockV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(RetrieveThingResponse.of(thingId, thingExpected.toJson(versionFieldSelector),
                        retrieveThing.getDittoHeaders()));
            }
        };
    }

    /** */
    @Test
    public void ensureSequenceNumberCorrectnessAfterRecovery() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElse(null);

                ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersMockV2);
                underTest.tell(modifyThing, getRef());
                expectMsgEquals(ModifyThingResponse.modified(thingId, modifyThing.getDittoHeaders()));

                // retrieve the thing's sequence number from recovered actor
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.toString(), JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify) //
                        .setRevision(versionExpected) //
                        .build();

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = createPersistenceActorFor(thing);

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersMockV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();
                underTest.tell(retrieveThing, getRef());

                expectMsgEquals(RetrieveThingResponse.of(thingId, thingExpected.toJson(versionFieldSelector),
                        retrieveThing.getDittoHeaders()));
            }
        };
    }

    /** */
    @Test
    public void createThingInV1AndRetrieveWithV1() {
        final String thingIdOfActor = "test.ns.v1:createThingInV1AndRetrieveWithV1";
        final Thing thingV1 = ThingsModelFactory.newThingBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setAttributes(THING_ATTRIBUTES)
                .setRevision(1)
                .setId(thingIdOfActor)
                .setPermissions(AUTHORIZED_SUBJECT, AccessControlListModelFactory.allPermissions())
                .build();

        final DittoHeaders dittoHeadersV1 = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .authorizationSubjects(AUTH_SUBJECT)
                .build();

        final CreateThing createThingV1 = CreateThing.of(thingV1, null, dittoHeadersV1);

        final RetrieveThing retrieveThingV1 = RetrieveThing.of(thingIdOfActor, dittoHeadersV1);
        final RetrieveThingResponse retrieveThingResponseV1 =
                RetrieveThingResponse.of(thingIdOfActor, thingV1, dittoHeadersV1);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingV1);
                underTest.tell(createThingV1, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                underTest.tell(retrieveThingV1, getRef());
                expectMsgEquals(retrieveThingResponseV1);
            }
        };
    }

    /**
     */
    @Test
    public void createThingInV1AndRetrieveWithV2() {
        final String thingIdOfActor = "test.ns.v1:createThingInV1AndRetrieveWithV2";
        final Thing thingV1 = ThingsModelFactory.newThingBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setAttributes(THING_ATTRIBUTES)
                .setRevision(1)
                .setId(thingIdOfActor)
                .setPermissions(AUTHORIZED_SUBJECT, AccessControlListModelFactory.allPermissions())
                .build();

        final DittoHeaders dittoHeadersV1 = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .authorizationSubjects(AUTH_SUBJECT)
                .build();
        final DittoHeaders dittoHeadersV2 = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .authorizationSubjects(AUTH_SUBJECT)
                .build();

        final CreateThing createThingV1 = CreateThing.of(thingV1, null, dittoHeadersV1);

        final RetrieveThing retrieveThingV2 = RetrieveThing.of(thingIdOfActor, dittoHeadersV2);
        final RetrieveThingResponse retrieveThingResponseV2 =
                RetrieveThingResponse.of(thingIdOfActor, thingV1, dittoHeadersV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingV1);
                underTest.tell(createThingV1, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                underTest.tell(retrieveThingV2, getRef());
                expectMsgEquals(retrieveThingResponseV2);
            }
        };
    }

    /**
     */
    @Test
    public void createThingInV1AndUpdateWithV2() {
        final String thingId = "test.ns.v1:createThingInV1AndUpdateWithV2";
        final Thing thingV1 = ThingsModelFactory.newThingBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setAttributes(THING_ATTRIBUTES)
                .setRevision(1)
                .setId(thingId)
                .setPermissions(AUTHORIZED_SUBJECT, AccessControlListModelFactory.allPermissions())
                .build();

        final DittoHeaders dittoHeadersV1 = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .authorizationSubjects(AUTH_SUBJECT)
                .build();
        final DittoHeaders dittoHeadersV2 = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .authorizationSubjects(AUTH_SUBJECT)
                .build();

        final CreateThing createThingV1 = CreateThing.of(thingV1, null, dittoHeadersV1);

        final Thing thingV2WithPolicy = thingV1.toBuilder()
                .removeAllPermissions()
                .setPolicyId(thingId)
                .build();

        final ModifyThing modifyThingV2 = ModifyThing.of(thingId, thingV2WithPolicy, null, dittoHeadersV2);

        final RetrieveThing retrieveThingV2 = RetrieveThing.of(thingId, dittoHeadersV2);
        final RetrieveThingResponse retrieveThingResponseV2 = RetrieveThingResponse.of(thingId, thingV2WithPolicy,
                dittoHeadersV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingV1);

                underTest.tell(createThingV1, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingV1);

                underTest.tell(modifyThingV2, getRef());
                expectMsgEquals(ModifyThingResponse.modified(thingId, modifyThingV2.getDittoHeaders()));

                underTest.tell(retrieveThingV2, getRef());
                expectMsgEquals(retrieveThingResponseV2);
            }
        };
    }

    /** */
    @Test
    public void responsesDuringInitializationAreSentWithDittoHeaders() {
        new JavaTestKit(actorSystem) {
            {
                final String thingId = "org.eclipse.ditto:myThing";
                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .authorizationSubjects("authSubject")
                        .correlationId("correlationId")
                        .source("source")
                        .schemaVersion(JsonSchemaVersion.LATEST)
                        .build();

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeaders);
                final ThingNotAccessibleException thingNotAccessibleException =
                        ThingNotAccessibleException.newBuilder(thingId)
                                .dittoHeaders(dittoHeaders)
                                .build();

                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(thingNotAccessibleException);
            }
        };
    }

    /** */
    @Test
    public void ensureModifiedCorrectnessAfterCreation() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();
                assertThat(createThingResponse.getThingCreated()).isPresent();
                assertThat(createThingResponse.getThingCreated().get())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // retrieve thing
                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thing.getId().orElse(null), dittoHeadersMockV1)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActor.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);
            }
        };
    }

    /** */
    @Test
    public void ensureModifiedCorrectnessAfterModification() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thing.getId().orElse(null), dittoHeadersMockV1)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActor.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // modify thing
                while (!Instant.now().isAfter(createThingResponseTimestamp)) {
                    waitMillis(10);
                }
                final ModifyThing modifyThing = ModifyThing.of(thing.getId().orElse(null), thing, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(modifyThing, getRef());
                expectMsgClass(ModifyThingResponse.class);
                final Instant modifyThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveModifiedThing =
                        RetrieveThing.getBuilder(thing.getId().orElse(null), dittoHeadersMockV1)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActor.tell(retrieveModifiedThing, getRef());
                final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isModifiedAfter(createThingResponseTimestamp);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isNotModifiedAfter(modifyThingResponseTimestamp);
            }
        };
    }

    /** */
    @Test
    public void ensureModifiedCorrectnessAfterRecovery() {
        new JavaTestKit(actorSystem) {
            {
                final Thing thing = createThingV1WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersMockV1);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // retrieve thing from recovered actor
                final ActorRef thingPersistenceActorRecovered = createPersistenceActorFor(thing);
                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thing.getId().orElse(null), dittoHeadersMockV1)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActorRecovered.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);

                assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
            }
        };
    }

    private static void assertThingInResponse(final Thing actualThing, final Thing expectedThing) {
        // Policy entries are ignored by things-persistence.
        assertThat(actualThing).hasEqualJson(expectedThing, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
    }

    private static void assertThingInResponseV2(final Thing actualThing, final Thing expectedThing) {
        // Policy entries are ignored by things-persistence.
        final Thing expectedThingWithoutPolicyEntries = expectedThing.setPolicyId(expectedThing.getId().get());
        assertThat(actualThing).hasEqualJson(expectedThingWithoutPolicyEntries, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
    }

    private ActorRef createPersistenceActorFor(final Thing thing) {
        return createPersistenceActorFor(thing.getId().orElse(null));
    }

}
