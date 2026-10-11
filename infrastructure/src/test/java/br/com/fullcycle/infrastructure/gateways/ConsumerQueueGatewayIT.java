package br.com.fullcycle.infrastructure.gateways;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConsumerQueueGatewayIT extends IntegrationTest {

    @Autowired
    private ConsumerQueueGateway consumerQueueGateway;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cancelar os tickets do evento ao consumir o EventCancelled da fila")
    public void testConsumeEventCancelled() throws Exception {
        // given
        final var anEventId = EventId.unique();
        final var anotherEventId = EventId.unique();

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var anotherTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anotherEventId));

        final var content = mapper.writeValueAsString(new EventCancelled(anEventId));

        // when
        consumerQueueGateway.publish(content);

        // then
        assertAllTicketsCancelled(anEventId);

        final var actualAnotherTicket = ticketRepository.ticketOfId(anotherTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.PENDING, actualAnotherTicket.status());
    }

    @Test
    @DisplayName("Deve reprocessar o EventCancelled sem erro")
    public void testConsumeEventCancelledTwice() throws Exception {
        // given
        final var anEventId = EventId.unique();

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));

        final var content = mapper.writeValueAsString(new EventCancelled(anEventId));
        consumerQueueGateway.publish(content);

        // when
        Assertions.assertDoesNotThrow(() -> consumerQueueGateway.publish(content));

        // then
        assertAllTicketsCancelled(anEventId);
    }

    private void assertAllTicketsCancelled(final EventId anEventId) {
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);
        Assertions.assertFalse(actualTickets.isEmpty());
        actualTickets.forEach(it -> Assertions.assertEquals(TicketStatus.CANCELLED, it.status()));
    }
}
