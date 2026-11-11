package com.allobank.splitbill.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "splitbill.github-username=rezihandianto")
@AutoConfigureMockMvc
class SplitBillApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String createGroup() throws Exception {
        String body = mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bali Trip", "participants": ["Alice", "Bob", "Carol"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void postJson(String url, String json, int expectedStatus) throws Exception {
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void fullFlowFromGroupCreationToSettlementAndPayment() throws Exception {
        String id = createGroup();

        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Dinner", "amount": 300.00, "paid_by": "Alice", "category": "Food"}
                """, 201);
        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Taxi", "amount": 90.00, "paid_by": "Bob", "category": "transport",
                 "split_type": "EXACT",
                 "splits": [{"participant": "Alice", "value": 30.00}, {"participant": "Carol", "value": 60.00}]}
                """, 201);

        // Alice net +170, Bob net -10, Carol net -160; total 390; service charge 2% = 7.80
        mockMvc.perform(get("/api/v1/groups/" + id + "/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_expenses").value(390.00))
                .andExpect(jsonPath("$.service_charge_pct").value(2))
                .andExpect(jsonPath("$.service_charge_amount").value(7.80))
                .andExpect(jsonPath("$.settled").value(false))
                .andExpect(jsonPath("$.transfers", hasSize(2)))
                .andExpect(jsonPath("$.transfers[0].from").value("Carol"))
                .andExpect(jsonPath("$.transfers[0].to").value("Alice"))
                .andExpect(jsonPath("$.transfers[0].amount").value(160.00))
                .andExpect(jsonPath("$.category_summary.food").value(300.00))
                .andExpect(jsonPath("$.category_summary.transport").value(90.00));

        postJson("/api/v1/groups/" + id + "/payments", """
                {"from": "Carol", "to": "Alice", "amount": 160.00, "note": "cash"}
                """, 201);

        mockMvc.perform(get("/api/v1/groups/" + id + "/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].from").value("Bob"))
                .andExpect(jsonPath("$.transfers[0].amount").value(10.00));

        postJson("/api/v1/groups/" + id + "/payments", """
                {"from": "Bob", "to": "Alice", "amount": 10.00}
                """, 201);

        mockMvc.perform(get("/api/v1/groups/" + id + "/settlement"))
                .andExpect(jsonPath("$.settled").value(true))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
    }

    @Test
    void rejectsPaymentLargerThanOutstandingDebt() throws Exception {
        String id = createGroup();
        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Dinner", "amount": 300.00, "paid_by": "Alice"}
                """, 201);

        postJson("/api/v1/groups/" + id + "/payments", """
                {"from": "Bob", "to": "Alice", "amount": 100.01}
                """, 422);
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        String id = createGroup();

        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Bad", "amount": -5, "paid_by": "Alice"}
                """, 400);
        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Bad", "amount": 10.123, "paid_by": "Alice"}
                """, 400);
        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Unknown payer", "amount": 10.00, "paid_by": "Mallory"}
                """, 400);
        postJson("/api/v1/groups/" + id + "/expenses", """
                {"description": "Bad enum", "amount": 10.00, "paid_by": "Alice", "split_type": "WHATEVER"}
                """, 400);
        postJson("/api/v1/groups", """
                {"name": "Solo", "participants": ["OnlyOne"]}
                """, 400);
    }

    @Test
    void returnsNotFoundForUnknownGroupAndBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/groups/" + UUID.randomUUID() + "/settlement"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/groups/not-a-uuid/settlement"))
                .andExpect(status().isBadRequest());
    }
}
