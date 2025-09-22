package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    // CONSTANTS FOR PRICES
    private static final int MAX_TICKETS_PER_CUSTOMER = 25;
    private static final int TICKET_FOR_ADULT = 25;
    private static final int TICKET_FOR_CHILD = 15;
    private static final int TICKER_FOR_INFANT = 0;

    // THIRD PARTY SERVICES
    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    // INITIALISE THIRD PARTY SERVICES
    public TicketServiceImpl(TicketPaymentService ticketPaymentService, SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        // VALIDATE INPUT
        validateAccountId(accountId);
        validateTicketTypeRequest(ticketTypeRequests);

        // CALCULATE TOTALS
        TICKET_RECORD calculatedTickets = calculateTickets(ticketTypeRequests);

        // VALIDATE RULES
        validateTicketCalculations(calculatedTickets);

        // PURCHASE
        ticketPaymentService.makePayment(accountId, calculatedTickets.totalPrice());
        seatReservationService.reserveSeat(accountId, calculatedTickets.totalSeats());
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            // Preferably add message like, no account id found
            throw new InvalidPurchaseException();
        }
    }

    private void validateTicketTypeRequest(TicketTypeRequest... ticketTypeRequests) {
        // Check if we have tickets
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException();
        }
        // Check individual request for tickets
        for (TicketTypeRequest ticketTypeRequest : ticketTypeRequests) {
            if (ticketTypeRequest == null || ticketTypeRequest.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException();
            }
        }
    }

    private void validateTicketCalculations(TICKET_RECORD ticketRecord) {
        // RULE: No tickets for kids without adult supervision
        if ((ticketRecord.totalChildTickets() >= 0 || ticketRecord.totalInfantTickets() >= 0) && ticketRecord.totalAdultTickets() <= 1) {
            throw new InvalidPurchaseException();
        }

        // RULE: No more than 25 tickets in one purchase
        if (ticketRecord.totalTickets() > MAX_TICKETS_PER_CUSTOMER) {
            throw new InvalidPurchaseException();
        }

        // RULE: Infants must sit on parent's Lap
        if (ticketRecord.totalInfantTickets() > 0 && ticketRecord.totalInfantTickets() > ticketRecord.totalAdultTickets()) {
            throw new InvalidPurchaseException();
        }

    }

    @SuppressWarnings("ConstantConditions")
    private TICKET_RECORD calculateTickets(TicketTypeRequest... ticketTypeRequests) {
        int totalPrice = 0, totalSeats = 0, totalTickets = 0, totalAdultTickets = 0, totalChildTickets = 0, totalInfantTickets = 0;

        for (TicketTypeRequest ticketTypeRequest : ticketTypeRequests) {
            int numberOfTickets = ticketTypeRequest.getNoOfTickets();
            totalTickets += numberOfTickets;

            switch (ticketTypeRequest.getTicketType()) {
                case ADULT -> {
                    totalPrice += numberOfTickets * TICKET_FOR_ADULT;
                    totalSeats += numberOfTickets;
                    totalAdultTickets += numberOfTickets;
                }
                case CHILD -> {
                    totalPrice += numberOfTickets * TICKET_FOR_CHILD;
                    totalSeats += numberOfTickets;
                    totalChildTickets += numberOfTickets;
                }
                case INFANT -> {
                    // ConstantConditions Suppressed, TICKER_FOR_INFANT is always 0;
                    // Using final TICKER_FOR_INFANT for ease of change.
                    totalPrice += numberOfTickets * TICKER_FOR_INFANT;
                    // counting towards the ticket limit, no seat counted.
                    totalInfantTickets += numberOfTickets;
                }
            }
        }

        return new TICKET_RECORD(totalPrice, totalSeats, totalTickets, totalAdultTickets, totalChildTickets, totalInfantTickets);

    }

    private record TICKET_RECORD(int totalPrice, int totalSeats, int totalTickets, int totalAdultTickets,
                                 int totalChildTickets, int totalInfantTickets) {
    }
}
