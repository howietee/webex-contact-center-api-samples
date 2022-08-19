package com.webexcc.api.captures.controller;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
//import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.webexcc.api.captures.service.ApiService;
import com.webexcc.api.captures.service.AuthService;
import com.webexcc.api.captures.service.ScheduledTasks;
import com.webexcc.api.captures.util.HtmlRender;
import com.webexcc.api.model.AgentsActivities;
import com.webexcc.api.model.Capture;
import com.webexcc.api.model.CaptureAttributes;
import com.webexcc.api.model.Captures;
import com.webexcc.api.model.Recording;

@RestController
@CrossOrigin
@RequestMapping("/")
public class WebRestController {
	static Logger logger = LoggerFactory.getLogger(WebRestController.class);

	String baseURL = "https://webexapis.com/v1";// new

	@Autowired
	HtmlRender htmlRender;

	@Autowired
	AuthService authService;

	@Autowired
	ScheduledTasks scheduledTasks;

	@Autowired
	ApiService apiService;

	public WebRestController() {
		super();
		new File("data").mkdirs();
		new File("data/Authentication.obj").delete();
	}

	/**
	 * force all traffic to index.html
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public Object root(HttpServletRequest request, HttpServletResponse response, @RequestParam final Map<String, String> inboundParameters) {
		logger.info("inboundParameters:{}", inboundParameters);
		try {
			java.util.LinkedHashMap<?, ?> map = (java.util.LinkedHashMap<?, ?>) inboundParameters;
			try {
				/**
				 * STEP 3 - already logged in STEP 2 only if authorized STEP 1 only if it is the
				 * 1st time to the web page
				 */
				//

				// STEP 3
				if (authService.getAuthentication() != null) {
					if (authService.getAuthentication().getAccess_token() == null) {
						throw new Exception("Need to login");
					}
					logger.info("Logged in");
					return homePage(request, response);
				}

				/**
				 * STEP 2 - get the token after the authorize request in STEP 1 below
				 */
				logger.info("authorized but not logged in");
				// this will cause an exception if code is not set. THIS IS A GOOD THING
				try {
					map.get("code").toString();
				} catch (Exception e) {
					throw new Exception("need to login");
				}
				authService.accessToken(map);
				scheduledTasks.setAuthService(authService);
				apiService.setAuthentication(scheduledTasks.getAuthService().getAuthentication());

				return homePage(request, response);
			}

			catch (java.lang.NullPointerException e) {
				authService.setAuthentication(null);
				return "Clear cache and try again.";
			} catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException e) {
				logger.error("UnrecognizedPropertyException", e);
				return "{\"Exception\":\"" + e.getOriginalMessage() + "\"}";
			} catch (Exception e) {
				/**
				 * STEP 1 - make the authorize request
				 */
				logger.info("1st time to web site");
				String login = authService.authorize();
				response.sendRedirect(login);
			}
		} catch (Exception e) {
			return "{\"Exception\":\"" + e.getMessage() + "\"}";
		}

		return "{}";

	}

	@RequestMapping(value = "/homePage", method = RequestMethod.GET)
	@ResponseBody
	public Object homePage(HttpServletRequest request, HttpServletResponse response) {
		return this.getCaptures(request, response);
	}

	@RequestMapping(value = "/captures", method = RequestMethod.GET)
	public Object getCaptures(HttpServletRequest request, HttpServletResponse response) {
		StringBuffer sb = new StringBuffer("<html>\n");
		htmlRender.header(sb);
		Captures oCaptures = new Captures();
		String search = "";

		try {
			// date time
			htmlRender.form(request, sb);
			/**
			 * do the work
			 */
			try {
				// info from html form
				request.getParameter("date").toString();
				request.getParameter("days").toString();
				String date = request.getParameter("date");
				int year = Integer.parseInt(date.split("-")[0]);
				int month = Integer.parseInt(date.split("-")[1]);
				int day = Integer.parseInt(date.split("-")[2]);
				Calendar c = Calendar.getInstance();
				c.set(Calendar.YEAR, year);
				c.set(Calendar.MONTH, --month);
				c.set(Calendar.DATE, day);
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);

				int days = Integer.parseInt(request.getParameter("days").toString());
				// logger.info("days to go backwards:{}", days);
				this.processTasks(oCaptures, c, days);
				logger.info("oCaptures.data.size :{}", oCaptures.getData().size());

				try {
					search = (String) request.getParameter("search").toLowerCase();
					logger.info("search :{}", search);
				} catch (Exception e) {
				}
				this.processCaptures(sb, oCaptures, search);
			} catch (Exception e) {
				logger.error("Exception:{}", e.getMessage());
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.info("getCause:{}:", e.getCause());
			logger.info("getLocalizedMessage:{}:", e.getLocalizedMessage());
			logger.info("getMessage:{}:", e.getMessage());
			return "{\"Exception\":\"" + StringEscapeUtils.escapeJson(e.getMessage()) + "\"}";
		}
		htmlRender.footer(sb);

		return sb.toString();
	}

	private void processCaptures(StringBuffer sb, Captures oCaptures, String search) {
		search = search.trim();
		List<Capture> data = oCaptures.getData();
		for (Iterator<Capture> iterator = data.iterator(); iterator.hasNext();) {
			Capture capture2 = iterator.next();
			List<Recording> recording = capture2.getRecording();
			for (Recording record : recording) {
				if (search.length() > 0) {
					// only print the matching search string
					if (record.getId().contains(search)) {
						htmlRender.printCaptureRecording(record, sb);
						writeFileToDisk(record);
					}
				} else {
					String fileName = writeFileToDisk(record);
//					htmlRender.printCaptureRecording2(record, sb, fileName);
					htmlRender.printCaptureAttributes(record, sb);
				}
				
			}
		}
	}

	private String writeFileToDisk(Recording record) {
		try {
//			CaptureAttributes attributes = record.getAttributes();
//			String fileName = record.getId() + ".wav";
//			InputStream in = new URL(attributes.getFilePath()).openStream();
//				Files.copy(in, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
//		      return fileName;
			/**
			 * 
			 */
			CaptureAttributes attributes = record.getAttributes();
			String fileName = record.getId() + "";
			InputStream in = new URL(attributes.getFilePath()).openStream();
			File file = File.createTempFile(fileName, ".wav");
			logger.info("file: {}", file.getAbsolutePath());
			file.deleteOnExit();
			Files.copy(in, Paths.get(file.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
			return file.getAbsolutePath();
		} catch (Exception e) {
			logger.error("Exception:{}", e.getMessage());
			return e.getMessage();
		}
	}

	private void processTasks(Captures oCaptures, Calendar c, int days) throws Exception {
		for (int i = 1; i <= days; i++) {
			logger.info("c.getTime: PROCESSing... DATE :{}", c.getTime());
			Map<String, String> taskMap = new HashMap<String, String>();
			AgentsActivities oAgentsActivities = apiService.getAgentsActivitiesForAGivenDay("telephony", c);

			// remove duplicates and null tasks
			oAgentsActivities.getData().stream().forEach(agentsActivity -> {
				if (agentsActivity.getTaskId() != null) {
					taskMap.put(agentsActivity.getTaskId(), agentsActivity.getTaskId());
				}
			});
			StringBuffer taskIds = new StringBuffer();
			int x = 0;
			for (Map.Entry<String, String> entry : taskMap.entrySet()) {
				x++;
				taskIds.append("\"" + entry.getValue() + "\",");
				// can only ask for 10 taskIds at a time
				if (x % 10 == 0) {
					taskIds.setLength(taskIds.length() - 1);
					Captures tmp = apiService.captures(taskIds);
					oCaptures.getData().addAll(tmp.getData());
					taskIds = new StringBuffer();
				}
			}
			// get the left over taskIds
			if (taskIds.length() > 0) {
				taskIds.setLength(taskIds.length() - 1);
				Captures tmp = apiService.captures(taskIds);
				oCaptures.getData().addAll(tmp.getData());
			}

			c.set(Calendar.HOUR_OF_DAY, 0);
			c.add(Calendar.DATE, -2);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
		}
		logger.info("DONE: PROCESSing...");
	}

}