package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


class TicketServiceImplTest {
    @Mock
    private TicketPaymentService ticketPaymentService;

    @Mock
    private SeatReservationService seatReservationService;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketPaymentService = mock(TicketPaymentService.class);
        seatReservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(ticketPaymentService, seatReservationService);
    }

    // HAPPY PATH

    @Test
    void shouldSuccessfullyPurchaseTicketsForAdults() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest twoAdultTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);

        ticketService.purchaseTickets(ACCOUNT_ID, twoAdultTickets);

        // two adult tickets 25*2;
        verify(ticketPaymentService).makePayment(ACCOUNT_ID, 50);
        // two seats reserved
        verify(seatReservationService).reserveSeat(ACCOUNT_ID, 2);
    }

    @Test
    void shouldSuccessfullyPurchaseTicketsForFamily() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest parentTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest childTicket = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);

        ticketService.purchaseTickets(ACCOUNT_ID, parentTickets, childTicket);

        // 25*2+15+0, 3 seats.
        verify(ticketPaymentService).makePayment(ACCOUNT_ID, 65);
        verify(seatReservationService).reserveSeat(ACCOUNT_ID, 3);
    }

    @Test
    void shouldSuccessfullyPurchaseTicketsForHugeFamily() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest adults1GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest adults2GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest adults3GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 3);
        TicketTypeRequest child1GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest child2GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);
        TicketTypeRequest child3GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);
        TicketTypeRequest infant1GroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);
        TicketTypeRequest rugratsGroupTickets = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 7);

        ticketService.purchaseTickets(ACCOUNT_ID, adults1GroupTickets,
                adults2GroupTickets,
                adults3GroupTickets,
                child1GroupTickets,
                child2GroupTickets,
                child3GroupTickets,
                infant1GroupTickets,
                rugratsGroupTickets);

        // 6 Adults, Price 6*25, Seats 6, Tickets 6
        // 6 Children, Price 6*15, Seats 6, Tickets 6
        // 8 Infants, Price 0, Seats 0, Tickets 8
        // 20, 6*25+6*15, Seats 12, Tickets 6+6
        verify(ticketPaymentService).makePayment(ACCOUNT_ID, 240);
        verify(seatReservationService).reserveSeat(ACCOUNT_ID, 12);
    }

    // RULES

    @Test
    void shouldNotChargeInfantsOrAllocateSeats() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest parentTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest rugrats = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 7);

        ticketService.purchaseTickets(ACCOUNT_ID, parentTickets, rugrats);

        verify(ticketPaymentService).makePayment(ACCOUNT_ID, 50);
        verify(seatReservationService).reserveSeat(ACCOUNT_ID, 2);
    }

    @Test
    void shouldAllowMaxTickets() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest maxAllowed = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 25);

        assertDoesNotThrow(() -> ticketService.purchaseTickets(ACCOUNT_ID, maxAllowed));

        verify(ticketPaymentService).makePayment(ACCOUNT_ID, 625);
        verify(seatReservationService).reserveSeat(ACCOUNT_ID, 25);
    }

    // EXCEPTIONS

    @Test
    void shouldThrowExceptionForMoreThan25Tickets() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest maxAllowedPlus1 = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26);

        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(ACCOUNT_ID, maxAllowedPlus1));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForMoreThan25TicketsAcrossMultipleRequests() {
        long ACCOUNT_ID = 1L;
        TicketTypeRequest halfMaxAdultsTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 13);
        TicketTypeRequest halfMaxInfantTickets = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 13);

        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(ACCOUNT_ID, halfMaxAdultsTickets, halfMaxInfantTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForChildTicketsWithoutAdult() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest childTickets = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, childTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForInfantTicketsWithoutAdult() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest rugrats = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 7);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, rugrats));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForChildAndInfantTicketsWithoutAdult() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest kids = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);
        TicketTypeRequest rugrats = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 5);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, kids, rugrats));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForNullAccountId() {
        TicketTypeRequest adultTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(null, adultTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForZeroAccountId() {
        Long ACCOUNT_ID = 0L;
        TicketTypeRequest adultTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, adultTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForNegativeAccountId() {
        Long ACCOUNT_ID = -1L;
        TicketTypeRequest adultTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, adultTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForNullTicketRequests() {
        Long ACCOUNT_ID = 1L;

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, (TicketTypeRequest[]) null));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForEmptyTicketRequests() {
        Long ACCOUNT_ID = 1L;

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForNullTicketRequest() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest adultTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, adultTickets, null));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForZeroTickets() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest zeroTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 0);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, zeroTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    void shouldThrowExceptionForNegativeTickets() {
        Long ACCOUNT_ID = 1L;
        TicketTypeRequest negativeTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, -1);

        assertThrows(InvalidPurchaseException.class, () ->
                ticketService.purchaseTickets(ACCOUNT_ID, negativeTickets));

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }


}