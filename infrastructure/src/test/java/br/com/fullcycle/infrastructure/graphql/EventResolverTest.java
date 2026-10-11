package br.com.fullcycle.infrastructure.graphql;

import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@AutoConfigureGraphQlTester
@SpringBootTest
class EventResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private EventRepository eventRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve criar um evento")
    public void testCreateEvent() {
        final var query = """
                mutation {
                    createEvent(input: { name: "Disney on Ice", date: "2021-01-01", totalSpots: 100, partnerId: "%s" }) {
                        id
                        name
                        date
                        totalSpots
                    }
                }
                """.formatted(disney.partnerId().value());

        graphQlTester.document(query)
                .execute()
                .path("createEvent.id").hasValue()
                .path("createEvent.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("createEvent.date").entity(String.class).isEqualTo("2021-01-01")
                .path("createEvent.totalSpots").entity(Integer.class).isEqualTo(100);
    }

    @Test
    @DisplayName("Deve cancelar um evento")
    public void testCancelEvent() {
        final var anEvent = createEvent();
        final var eventId = anEvent.eventId().value();

        final var query = """
                mutation {
                    cancelEvent(id: "%s") {
                        id
                        status
                    }
                }
                """.formatted(eventId);

        graphQlTester.document(query)
                .execute()
                .path("cancelEvent.id").entity(String.class).isEqualTo(eventId)
                .path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");

        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelEventTwice() {
        final var anEvent = createEvent();
        anEvent.cancel();
        eventRepository.update(anEvent);

        final var query = """
                mutation {
                    cancelEvent(id: "%s") {
                        id
                        status
                    }
                }
                """.formatted(anEvent.eventId().value());

        graphQlTester.document(query)
                .execute()
                .errors()
                .expect(error -> error.getPath().equals("cancelEvent"));
    }

    @Test
    @DisplayName("Deve obter um evento por id")
    public void testEventOfId() {
        final var eventId = createEvent().eventId().value();

        final var query = """
                query {
                    eventOfId(id: "%s") {
                        id
                        name
                        date
                        totalSpots
                        status
                    }
                }
                """.formatted(eventId);

        graphQlTester.document(query)
                .execute()
                .path("eventOfId.id").entity(String.class).isEqualTo(eventId)
                .path("eventOfId.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("eventOfId.date").entity(String.class).isEqualTo("2021-01-01")
                .path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(100)
                .path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Deve obter nulo ao buscar um evento não existente por id")
    public void testEventOfIdNotFound() {
        final var query = """
                query {
                    eventOfId(id: "%s") {
                        id
                    }
                }
                """.formatted(EventId.unique().value());

        graphQlTester.document(query)
                .execute()
                .path("eventOfId").valueIsNull();
    }

    private Event createEvent() {
        return eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
    }
}
