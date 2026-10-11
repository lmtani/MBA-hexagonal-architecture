package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

class TicketDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve buscar os tickets de um evento")
    public void testTicketsByEventId() throws Exception {
        // given
        final var anEventId = EventId.unique();
        final var anotherEventId = EventId.unique();

        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var aTicket2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anotherEventId));

        final var expectedTicketIds = Set.of(aTicket.ticketId(), aTicket2.ticketId());

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);

        // then
        Assertions.assertEquals(expectedTicketIds, actualTickets.stream().map(Ticket::ticketId).collect(Collectors.toSet()));
        actualTickets.forEach(it -> Assertions.assertEquals(anEventId, it.eventId()));
    }

    @Test
    @DisplayName("Deve retornar vazio quando o evento não possui tickets")
    public void testTicketsByEventIdWithoutTickets() throws Exception {
        // when
        final var actualTickets = ticketRepository.ticketsByEventId(EventId.unique());

        // then
        Assertions.assertTrue(actualTickets.isEmpty());
    }

    @Test
    @DisplayName("Deve persistir o cancelamento de um ticket")
    public void testUpdateCancelledTicket() throws Exception {
        // given
        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), EventId.unique()));

        // when
        aTicket.cancel();
        ticketRepository.update(aTicket);

        // then
        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.CANCELLED, actualTicket.status());
    }
}
