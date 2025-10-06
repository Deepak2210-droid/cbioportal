/*
 * Copyright (c) 2015 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cbioportal.application.proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

@Controller
@RequestMapping("/api/proxy")
public class LegacyProxyController {

  private String bitlyURL;
  private String sessionServiceURL;
  private Boolean enableOncokb;

  @Value("${bitly.url:''}")
  public void setBitlyURL(String property) {
    this.bitlyURL = property;
  }

  @Value("${session.service.url:''}") // default is empty string
  public void setSessionServiceURL(String property) {
    this.sessionServiceURL = property;
  }

  @Value("${show.oncokb:true}")
  public void setEnableOncokb(Boolean property) {
    if (property == null) {
      property = true;
    }
    this.enableOncokb = property;
  }

  // This is a general proxy for future use.
  // Please modify and improve it as needed with your best expertise. The author does not have fully
  // understanding
  // of JAVA proxy when creating this proxy.
  // Created by Hongxin
  @RequestMapping(value = "/{path}")
  public @ResponseBody String getProxyURL(
      @PathVariable String path,
      @RequestBody(required = false) String body,
      HttpMethod method,
      HttpServletRequest request,
      HttpServletResponse response)
      throws URISyntaxException, IOException {
    Map<String, String> pathToUrl = new HashMap<>();

    pathToUrl.put("bitly", bitlyURL);
    pathToUrl.put("3dHotspots", "https://www.3dhotspots.org/api/hotspots/3d/");

    // Validate path strictly: only allow exact keys in pathToUrl
    if (!pathToUrl.containsKey(path)) {
      response.sendError(400, "Invalid proxy path.");
      return "";
    }

    String baseUrl = pathToUrl.get(path);

    if (path != null && StringUtils.startsWithIgnoreCase(path, "oncokb") && !enableOncokb) {
      response.sendError(403, "OncoKB service is disabled.");
      return "";
    }

    // Validate and sanitize query parameters
    String sanitizedQuery = null;
    if (method.equals(HttpMethod.GET) && request.getQueryString() != null) {
      sanitizedQuery = sanitizeQueryString(request.getQueryString());
    }

    String URL = baseUrl;
    if (sanitizedQuery != null && !sanitizedQuery.isEmpty()) {
      URL += "?" + sanitizedQuery;
    }

    return respProxy(URL, method, body, response);
  }

  private String sanitizeQueryString(String query) throws IOException {
    // Allow only alphanumeric, underscore, dash, dot, equal sign and ampersand
    // Decode first to avoid double encoding attacks
    String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.name());

    // Split parameters and filter by whitelist
    StringBuilder sanitized = new StringBuilder();
    String[] params = decodedQuery.split("&");

    // Define whitelist of allowed parameter names (example: allow all alphanumeric keys)
    for (String param : params) {
      if (param.isEmpty()) {
        continue;
      }
      String[] kv = param.split("=", 2);
      String key = kv[0];
      String value = kv.length > 1 ? kv[1] : "";

      // Only allow keys with alphanumeric, underscore, dash
      if (!key.matches("[a-zA-Z0-9_\-]+")) {
        continue; // skip disallowed keys
      }

      // Escape value by URL encoding
      String encodedValue = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());

      if (sanitized.length() > 0) {
        sanitized.append("&");
      }
      sanitized.append(key).append("=").append(encodedValue);
    }

    return sanitized.toString();
  }

  private String respProxy(String url, HttpMethod method, Object body, HttpServletResponse response)
      throws IOException {
    try {
      RestTemplate restTemplate = new RestTemplate();
      URI uri = new URI(url);
      ResponseEntity<String> responseEntity =
          restTemplate.exchange(uri, method, new HttpEntity<>(body), String.class);
      return responseEntity.getBody();
    } catch (Exception exception) {
      String errorMessage = "Unexpected error: " + exception.getLocalizedMessage();
      response.sendError(503, errorMessage);
      return errorMessage;
    }
  }

  @RequestMapping(value = "/bitly", method = RequestMethod.GET)
  public @ResponseBody String getBitlyURL(
      HttpMethod method, HttpServletRequest request, HttpServletResponse response)
      throws URISyntaxException, IOException {
    String sanitizedQuery = "";
    if (request.getQueryString() != null) {
      sanitizedQuery = sanitizeQueryString(request.getQueryString());
    }
    String url = bitlyURL;
    if (!sanitizedQuery.isEmpty()) {
      url += "?" + sanitizedQuery;
    }
    return respProxy(url, method, null, response);
  }
}
