package com.codecool.krk.lucidmotors.queststore.handlers;

import com.codecool.krk.lucidmotors.queststore.enums.*;
import com.codecool.krk.lucidmotors.queststore.exceptions.DaoException;
import com.codecool.krk.lucidmotors.queststore.exceptions.IncorrectStateException;
import com.codecool.krk.lucidmotors.queststore.handlers.helpers.Cookie;
import com.codecool.krk.lucidmotors.queststore.models.*;
import com.codecool.krk.lucidmotors.queststore.views.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static com.codecool.krk.lucidmotors.queststore.enums.Roles.SETTINGS;

public class MainHandler implements HttpHandler {

    private Map<UUID, User> loggedUsers = new HashMap<>();
    private School school;

    public MainHandler(School school) {
        this.school = school;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        //reading state

        Activity activity;
        try {
            activity = getActivity(httpExchange);
        } catch (DaoException e) {
            e.printStackTrace();
            activity = new Activity(500, e.toString());
        } catch (IncorrectStateException e) {
            e.printStackTrace();
            activity = new Activity(302, "/");
        }

        sendResponse(activity, httpExchange);
    }

    private Activity getActivity(HttpExchange httpExchange)
            throws IOException, DaoException, IncorrectStateException {

        Activity activity;

        Map<String, String> formData = getFormData(httpExchange);
        formData = preventHtmlInjection(formData);

        User user = getUserByCookie(httpExchange);

        String uri = httpExchange.getRequestURI().getPath();
        URIResponse parsedURI = parseURI(uri);

        if(user == null) {
            activity = getUnloggedActivity(parsedURI, httpExchange, formData);
        } else if (isProperUser(parsedURI.getRole(), user)) {
            Cookie.renewCookie(httpExchange, "UUID");
            activity = getUserActivity(parsedURI, formData, user);
        } else {
            activity = getOtherActivity(parsedURI.getRole(), formData, user, httpExchange);
        }

        return activity;
    }

    private Activity getUnloggedActivity(URIResponse parsedURI, HttpExchange httpExchange, Map<String, String> formData) throws IOException, DaoException {
        switch (parsedURI.getRole()) {
            case SETTINGS:
                return new SettingsView(formData, httpExchange).getActivity();

            default:
                return new LoginView(this.school, formData, this.loggedUsers).getActivity(httpExchange);
        }

    }

    private Map<String , String> preventHtmlInjection(Map<String , String> formData) {

        for (String key : formData.keySet()) {
            String safeValue = formData.get(key).replace("<", "&lt;").replace(">", "&gt;");
            formData.put(key, safeValue);
        }

        return formData;
    }

    private Map<String,String> getFormData(HttpExchange httpExchange) throws IOException {
        Map<String, String> postValues = new HashMap<>();

        String method = httpExchange.getRequestMethod();

        if(method.equals("POST")) {
            InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String formData = br.readLine();
            
            if (formData != null) {
                String[] pairs = clearDeliveredFormData(formData);

                for (String pair : pairs) {
                    String[] splitedPair = pair.split("=");
                    String key = splitedPair[0];
                    String value = (splitedPair.length > 1) ? URLDecoder.decode(splitedPair[1], "UTF-8") : "";
                    postValues.put(key, value);
                }
            }
        }

        return postValues;
    }

    private String[] clearDeliveredFormData(String array) {
        Predicate<String> isBuiltJustByWhiteSpaces = s -> s.chars().allMatch(c -> c == ' ');
        Predicate<String> containsJustOneEqualSign = s -> 1 == s.chars().filter(c -> c == '=').count();

        return Arrays.stream(array.split("&")).filter(s -> !s.isEmpty())
                                                    .filter(s -> !isBuiltJustByWhiteSpaces.test(s))
                                                    .filter(containsJustOneEqualSign)
                                                    .toArray(String[]::new);
    }

    private boolean isProperUser(Roles role, User user) {
        return switchUser(user) == role;
    }

    static Roles switchUser(User user) {
        Roles userUrl;

        if (user instanceof Manager) {
            userUrl = Roles.MANAGER;
        } else if (user instanceof Mentor) {
            userUrl = Roles.MENTOR;
        } else if (user instanceof Student) {
            userUrl = Roles.STUDENT;
        } else {
            userUrl = null;
        }

        return userUrl;
    }

    public User getUserByCookie(HttpExchange httpExchange) {
        User user = null;
        String uuid = Cookie.getCookieValue(httpExchange, "UUID");
        if (uuid != null) {
            user = loggedUsers.getOrDefault(UUID.fromString(uuid), null);
        }

        return user;
    }

    private URIResponse parseURI (String uri) {
        String[] uriList = uri.split("/");
        URIResponse response;

        if (uriList.length == 2 && checkIsProperRole(uriList)) {
            Roles role = EnumUtils.getValue(Roles.class, uriList[1].toUpperCase());
            response = new URIResponse(role, Action.DEFAULT, "");
        } else if (uriList.length == 3 && checkIsProperRole(uriList)) {
            Roles role = EnumUtils.getValue(Roles.class, uriList[1].toUpperCase());
            String request = uriList[2].toUpperCase();
            response = new URIResponse(role, Action.getUserAction(role), request);
        } else {
            response = new URIResponse(Roles.DEFAULT, Action.DEFAULT, "");
        }

        return response;
    }

    public Activity getUserActivity(URIResponse response, Map<String, String> formData, User user)
            throws DaoException, IOException, IncorrectStateException {
        switch (response.getRole()) {
            case MANAGER:
                return new ManagerView(this.school, user, formData).getActivity((ManagerOptions) getProperAction(response));

            case MENTOR:
                return new MentorView(this.school, user, formData).getActivity((MentorOptions) getProperAction(response));
          
            case STUDENT:
                return new StudentView(user, formData).getActivity((StudentOptions) getProperAction(response));

        }
        return null;
    }

    private Enum getProperAction(URIResponse uriResponse) {
        Action action = uriResponse.getAction();
        String command = uriResponse.getCommand();

        return action.prepareCommand(uriResponse.getRole(), command);
    }

    private Activity getOtherActivity(Roles role, Map<String, String> formData, User user, HttpExchange httpExchange) throws DaoException {

        switch (role) {
            case LOGOUT:
                return new LogoutView(user, loggedUsers).getActivity();

            case CHAT:
                return new ChatView(formData).getActivity();

            case SETTINGS:
                return new SettingsView(formData, httpExchange).getActivity();

            default:
                return redirectByUser(user);

        }
    }

    public static Activity redirectByUser(User user) {
        String userUrl = "/";

        if (switchUser(user) != null) {
            userUrl = "/" + switchUser(user).toString().toLowerCase();
        }

        return new Activity(302, userUrl);
    }

    private Boolean checkIsProperRole(String[] uriList) {
        Boolean isProperRole;
        try {
            Roles.valueOf(uriList[1].toUpperCase());
            isProperRole = true;
        } catch (IllegalArgumentException e) {
            isProperRole = false;
        }

        return isProperRole;
    }

    private void sendResponse(Activity activity, HttpExchange httpExchange) throws IOException {
        if (activity.hasHeader()) {
            httpExchange.getResponseHeaders().add(activity.getHeaderName(), activity.getHeaderContent());
        }

        if (activity.getHttpStatusCode().equals(200)) {
            String response = activity.getAnswer();
            writeHttpOutputStream(activity.getHttpStatusCode(), response, httpExchange);

        } else if (activity.getHttpStatusCode().equals(302)) {
            String newLocation = activity.getAnswer();
            httpExchange.getResponseHeaders().set("Location", newLocation);
            httpExchange.sendResponseHeaders(302, -1);

        } else if (activity.getHttpStatusCode().equals(500)) {
            httpExchange.sendResponseHeaders(500, 0);

        } else {
            String response = "404 (Not Found)\n";
            int httpStatusCode = 404;
            writeHttpOutputStream(httpStatusCode, response, httpExchange);
        }
    }

    private void writeHttpOutputStream(int httpStatusCode, String response, HttpExchange httpExchange) throws IOException {
        final byte[] finalResponseBytes = response.getBytes("UTF-8");
        httpExchange.sendResponseHeaders(httpStatusCode, finalResponseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(finalResponseBytes);
        os.close();
    }
}
