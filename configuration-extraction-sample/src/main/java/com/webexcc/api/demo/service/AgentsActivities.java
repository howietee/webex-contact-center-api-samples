/**
 * Copyright (c) 2022
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.webexcc.api.demo.service;

import java.util.Calendar;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * The <code>AgentsActivities</code> is a class that implements the rest API to
 * extract data from the WebexCC platform.
 * 
 * @author jiwyatt
 * @since 2.0
 * @see agents/v1/get-agent-activities
 */

public class AgentsActivities extends ApiService {
	static Logger logger = LoggerFactory.getLogger(AgentsActivities.class);

	public AgentsActivities() {
	}

	/**
	 * 
	 * @param from
	 * @param to
	 * @param pageSize
	 * @throws Exception
	 */
	void getAgentsActivitiesByFromTo(long from, long to, int pageSize) throws Exception {
		String url = "";
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			headers.add("Authorization", "Bearer " + access_token);
			StringBuffer payload = new StringBuffer();
			HttpEntity<?> entity = new HttpEntity<>(payload.toString(), headers);
			url = baseURL2 + "/agents/activities?channelTypes=telephony" + "&from=" + from + "&to=" + to + "&pageSize=" + pageSize + "&orgId=" + organizationId;
			ResponseEntity<String> response1 = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			JSONObject json = new JSONObject(response1.getBody());
			logger.info("\n{}", json.toString(4));

		} catch (Exception e) {
			if (e.getMessage().startsWith("429 Too Many Requests")) {
				logger.warn("Too Many Requests so take a nap for 1 minute.");
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e1) {
				}
				getAgentsActivitiesByFromTo(from, to, +pageSize);
			}
			throw e;
		}
	}

	/**
	 * Entry point of the Java <code>AgentsActivities</code> program
	 * 
	 * @param args
	 */

	public static void main(String[] args) {
		try {
			Calendar cal = Calendar.getInstance();
			long to = cal.getTimeInMillis();
			cal.set(Calendar.HOUR_OF_DAY, -8);
			long from = cal.getTimeInMillis();

			new AgentsActivities().getAgentsActivitiesByFromTo(from, to, 900);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
