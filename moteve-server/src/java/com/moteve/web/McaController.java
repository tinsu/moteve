/*
 * Copyright 2009-2010 Moteve.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.moteve.web;

import com.moteve.domain.Group;
import com.moteve.domain.User;
import com.moteve.service.UserService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controller responsible for communication with Moteve Client Applications
 * (that run typically on mobile phones).
 *
 * @author Radek Skokan
 */
@Controller
public class McaController {

    /**
     * Buffer size for video streaming over HTTP
     */
    public static final int BUFFER_SIZE = 8192;

    private static final String PASSWORD_DELIMITER = "\\";

    private static final Logger logger = Logger.getLogger(McaController.class);

    @Autowired
    private UserService userService;

    /**
     * Before a Moteve Client Application (a mobile phone typically) starts any
     * communication with the server, it first needs to authenticate.
     * When the authentication process is successful, MCA is given a security
     * token that is then used when sending requests to the server.
     *
     * @param request contains HTTP header Moteve-Auth with email\password\device_description
     * @param response contains HTTP header Moteve-Token with the generated security token
     *          or AUTH_ERROR if the authentication failed. The token value is also in the
     *          response body.
     */
    @RequestMapping(value = "/mca/register.htm", method = RequestMethod.POST)
    public void registerMca(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader("Moteve-Auth");
        if (authHeader == null) {
            return;
        }

        int emailPasswordDelimiter = authHeader.indexOf("\\");
        int passwordDescDelimiter = authHeader.lastIndexOf("\\");
        if (emailPasswordDelimiter < 1 || passwordDescDelimiter < 1 || emailPasswordDelimiter == passwordDescDelimiter) {
            return;
        }

        String email = authHeader.substring(0, emailPasswordDelimiter);
        String password = authHeader.substring(emailPasswordDelimiter + 1, passwordDescDelimiter);
        String desc = authHeader.substring(passwordDescDelimiter + 1);

        logger.info("Authenticating MCA. E-mail=" + email + ", pwd=" + password + ", desc=" + desc);
        User user = userService.authenticate(email, password);
        PrintWriter out = null;
        try {
            out = new PrintWriter(response.getOutputStream());
            response.setContentType("text/html");
            if (user == null) {
                logger.info("MCA authentication failed for user " + email);
                response.setHeader("Moteve-Token", "AUTH_ERROR");
                out.print("AUTH_ERROR");
            } else {
                logger.info("MCA authentication successful for user " + email);
                String token = userService.registerMca(user, desc);
                response.setHeader("Moteve-Token", token);
                out.print(token);
            }
        } catch (IOException e) {
            logger.error(e);
        } finally {
            out.flush();
            out.close();
        }
    }

    /**
     * Provides list with names of Groups that the user has configured on the server.
     *
     * @param request must contain HTTP header Moteve-Token with the value
     *          received during device registration
     * @param response the HTTP body contains list of group names the user has.
     *          The groups are delimited by a backslash (\) letter.
     */
    @RequestMapping(value = "/mca/listGroups.htm", method = RequestMethod.POST)
    public void listGroups(HttpServletRequest request, HttpServletResponse response) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(response.getOutputStream());
            response.setContentType("text/html");
            String token = request.getHeader("Moteve-Token");
            if (token == null) {
                response.setHeader("Moteve-Token", "MISSING_TOKEN");
                out.print("MISSING_TOKEN");
                return;
            }

            User user = userService.getUserForDevice(token);
            if (user == null) {
                response.setHeader("Moteve-Token", "WRONG_TOKEN");
                out.print("WRONG_TOKEN");
                return;
            }

            StringBuffer groupNames = new StringBuffer();
            for (Group group : user.getGroups()) {
                groupNames.append(group.getName()).append("\\");
            }

            out.print(groupNames.toString());
        } catch (IOException e) {
            logger.error(e);
        } finally {
            out.flush();
            out.close();
        }
    }

    /**
     * HTTP POST method for uploading captured video parts.<br/>
     * The Process is:
     * <ul>
     * <li>Client sends <code>Moteve-Sequence</code> header containing <code>new</code>.
     * Also the <code>Moteve-Token</code> header is present.
     *
     * <li>Server replies with a video sequence number to be used in response header
     * <code>Moteve-Sequence</code>.</li>
     *
     * <li>Client is then sending the video parts in the POST data. Each request must contain
     * in its <code>Moteve-Sequence</code> header the obtained number and also
     * a <code>Moteve-Part</code> header with the video part number. They should start at 1.
     * Header <code>Moteve-Token</code> must be set.</li>
     *
     * <li>When all video parts has been uploaded, the client close the process by sending
     * the <code>Moteve-Sequence</code> header set to <code>close_<the_seq_number></code>.
     * Header <code>Moteve-Token</code> must be set.</li>
     * </ul>
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "/mca/upload.htm", method = RequestMethod.POST)
    public void uploadVideo(HttpServletRequest request, HttpServletResponse response) {
        logger.info("uploadVideo");

        PrintWriter out = null;

        try {
            out = new PrintWriter(response.getOutputStream());
            response.setContentType("text/html");

            String sequence = request.getHeader("Moteve-Sequence");
            if (sequence == null || sequence.length() == 0) {
                out.println("ERROR");
                out.println("Missing Moteve-Sequence parameter");

            } else if (sequence.equals("new")) {
                User user = getUser(request, response);
                if (user != null) {
//                    sequence = newVideo(request, response);
                    out.print(sequence);
                }

            } else if (sequence.startsWith("close_")) {
                if (checkToken(request, response)) {
                    sequence = sequence.substring("close_".length());
                    closeSequence(sequence);
                    out.println(sequence + " closed");
                }

            } else {
                if (checkToken(request, response)) {
                    String part = request.getHeader("Moteve-Part");
                    if (part == null || part.length() == 0) {
                        out.println("ERROR");
                        out.println("Missing Moteve-Part parameter");
                    } else {
                        writeFile(sequence, part, request.getInputStream());
                        out.println("OK");
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            out.flush();
            out.close();
        }
    }

    private void writeFile(String sequence, String part, ServletInputStream sis) throws IOException {
        FileOutputStream fos = null;
        File f;
        try {
            f = new File(buildFilePath(sequence, part));
            fos = new FileOutputStream(f);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            int totalSize = 0;
            while ((bytesRead = sis.read(buffer)) != -1) {
                totalSize += bytesRead;
                fos.write(buffer, 0, bytesRead);
            }
            logger.info("Bytes written: " + totalSize + ". Total file (" + f.getAbsolutePath() + ") size: " + f.length() + " B.");
        } finally {
            fos.flush();
            fos.close();
            sis.close();
        }
    }

    private void closeSequence(String sequence) {
        // TODO Auto-generated method stub
    }

    private String newSequence() {
        // TODO: use DB sequence
        String rand = String.valueOf((long) Math.random() * 1000);
        return System.currentTimeMillis() + rand;
    }

    private String buildFilePath(String sequence, String part) {
        return "C:/temp/moteve/" + sequence + "_" + part + ".3gp";
    }

    /**
     * Authenticates the user name with the password specified in the Moteve-Auth header.
     *
     * @param request
     * @param response
     * @return the User if the authentication was successfull. Otherwise null and also
     *      sets the header fields accordingly.
     */
    private User getUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String auth = request.getHeader("Moteve-Auth");
        if (auth == null || auth.length() == 0) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Moteve-Auth parameter");
            return null;
        }

        int delimiterPos = auth.indexOf(PASSWORD_DELIMITER);
        String username = auth.substring(0, delimiterPos);
        String password = auth.substring(delimiterPos);
        User user = userService.authenticate(username, password);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Moteve-Auth parameter");
        }

        return user;
    }

    /**
     * Checks if the security token in Moteve-Token belongs to the video identified
     * by Moteve-Sequence.
     * @param request
     * @param response
     * @return true if the token matches the video. Otherwise false and also sets the response header accordingly
     */
    private boolean checkToken(HttpServletRequest request, HttpServletResponse response) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}