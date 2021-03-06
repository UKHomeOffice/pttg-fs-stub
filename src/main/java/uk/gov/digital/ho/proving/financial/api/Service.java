package uk.gov.digital.ho.proving.financial.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.digital.ho.proving.financial.domain.BalanceSummary;
import uk.gov.digital.ho.proving.financial.exception.AccountNotFoundException;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static uk.gov.digital.ho.proving.financial.util.DateUtils.parseIsoDate;

@RestController
@ControllerAdvice
@RequestMapping(value = {"/accounts/"})
public class Service {

    static final String CONSENT_SUCCESS = "SUCCESS";

    static final String BANKCODE_5 = "bankcode 5";
    static final String BANKCODE_1 = "bankcode 1";
    static final String BANKCODE_2 = "bankcode 2";
    static final String BANKCODE_3 = "bankcode 3";
    static final String BANKCODE_4 = "bankcode 4";
    private static Logger LOGGER = LoggerFactory.getLogger(Service.class);

    @Autowired
    private DataService dataService;

    @RequestMapping(value = "{account}/balances", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<BarclaysAccountBalanceResponse> getBalanceRecordsForDateRange(
            @PathVariable(value = "account") String accountId,
            @RequestParam(value = "fromDate") String fromDateAsString,
            @RequestParam(value = "toDate") String toDateAsString) {

        String sortcode = accountId.substring(0, 6);
        String account = accountId.substring(6);
        LOGGER.debug(String.format("Financial Status Service STUB invoked for %s sortcode %s account between %s and %s", sortcode, account, fromDateAsString, toDateAsString));

        try {

            Optional<LocalDate> fromDate = parseIsoDate(fromDateAsString);
            if (!fromDate.isPresent()) {
                return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_1, "Parameter error: From date is invalid", HttpStatus.BAD_REQUEST);
            }

            Optional<LocalDate> toDate = parseIsoDate(toDateAsString);
            if (!toDate.isPresent()) {
                return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_2, "Parameter error: To date is invalid", HttpStatus.BAD_REQUEST);
            }

            //flat map - removes the nested optional (if it exists), map uses value if exists or returns optional with absent - so lookup not called
            Optional<BalanceSummary> statement = fromDate.flatMap(from ->
                    toDate.map(to ->
                            dataService.getStatement(sortcode, account, from, to)
                    )
            );

            return statement.map(ips -> {
                        if (CONSENT_SUCCESS.equals(ips.getConsent())) {
                            return new ResponseEntity<>(new BarclaysAccountBalanceResponse(ips), HttpStatus.OK);
                        } else {
                            return buildErrorResponse(new BarclaysAccountBalanceResponse(), "400", "Account-Holder consent unavailable", HttpStatus.BAD_REQUEST);
                        }
                    }
            ).orElse(buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_3, "Error retrieving test data", HttpStatus.NOT_FOUND));

        } catch (AccountNotFoundException e) {
            return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_4, "Resource not found", HttpStatus.NOT_FOUND);
        } catch (RuntimeException e) {
            LOGGER.error("Error retrieving test data", e);
            return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_5, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /*persist complete Balance summary (account information and balance records) throws FinancialStatusStubException if account already exists*/
    @RequestMapping(value = "accounts", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<?> createTestData(@RequestBody @Valid BalanceSummary testData) {

        LOGGER.info(String.format("Financial Status Service STUB invoked for testdata %s", testData));

        try {
            dataService.saveTestData(testData);
        } catch (RuntimeException e) {
            LOGGER.error("Error persisting test data", e);
            return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_5, "Error persisting testData test data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok().build();
    }


    @RequestMapping(value = "accounts", method = RequestMethod.DELETE, produces = "application/json")
    public ResponseEntity<BarclaysAccountBalanceResponse> deleteTestData() {

        try {
            dataService.initialiseTestData();
            return new ResponseEntity<>(new BarclaysAccountBalanceResponse(), HttpStatus.OK);

        } catch (Exception e) {
            LOGGER.error("Error deleting test data", e);
            return buildErrorResponse(new BarclaysAccountBalanceResponse(), BANKCODE_5, "Error deleting test data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "accounts", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<BalanceSummariesResponse> getAllTestData() {

        try {

            final List<BalanceSummary> balanceSummaries = dataService.getAllBalanceSummaries();

            return new ResponseEntity<>(new BalanceSummariesResponse(balanceSummaries), HttpStatus.OK);

        } catch (RuntimeException e) {
            LOGGER.error("Error retrieving test data", e);
            return buildErrorResponse(new BalanceSummariesResponse(), BANKCODE_5, "Error retrieving test data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected <U extends BaseResponse> ResponseEntity<U> buildErrorResponse(U response, String statusCode, String
            statusMessage, HttpStatus status) {
        ResponseStatus error = new ResponseStatus(statusCode, statusMessage);
        response.setStatus(error);
        return ResponseEntity.status(status)
                .contentType(
                        MediaType.APPLICATION_JSON)
                .body(response);
    }

}
