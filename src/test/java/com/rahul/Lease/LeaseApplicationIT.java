package com.rahul.Lease;

import com.rahul.Lease.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaseApplicationIT extends IntegrationTestBase {

	// Every @Query is validated at startup, so a broken query fails here
	@Test
	void contextLoads() {
	}

	@Test
	void apiDocsAreServed() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

}
