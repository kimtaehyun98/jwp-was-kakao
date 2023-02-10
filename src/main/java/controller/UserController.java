package controller;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import model.User;
import model.dto.Cookie;
import model.dto.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FileIoUtils;
import model.dto.MyHeaders;
import model.dto.MyParams;
import utils.UserFactory;
import webserver.response.ResponseEntity;
import webserver.session.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.stream.Collectors;

import static db.DataBase.*;
import static webserver.session.SessionManager.*;

public class UserController  implements MyController {

    private final Logger logger = LoggerFactory.getLogger(UserController.class);
    private int status = 200;
    private String redirectUrl = "";
    private String cookie;
    private byte[] body = null;

    @Override
    public boolean canHandle(MyHeaders headers, MyParams params) {
        String path = headers.get("path");
        return path.startsWith("/user");
    }

    @Override
    public ResponseEntity handle(MyHeaders headers, MyParams params, DataOutputStream dataOutputStream) {
        String path = headers.get("path");
        String contentType = headers.get("contentType");
        cookie = headers.getCookie().toString();
        status = 200;

        if(path.equals("/user/form.html") && headers.get("method").equals("GET")){
            createForm(path);
        }

        if(path.equals("/user/login.html") && headers.get("method").equals("GET")){
            loginForm(path);
        }

        if(path.equals("/user/list") && headers.get("method").equals("GET")){
            getUserList(contentType, cookie, dataOutputStream);
        }

        if(path.equals("/user/login_failed.html") && headers.get("method").equals("GET")){
            loginFailForm(path);
        }

        if(path.equals("/user/login") && headers.get("method").equals("GET")){
            alreadyLogin();
        }

        if(path.equals("/user/create") && headers.get("method").equals("POST")){
            createUser(params);
        }

        if(path.equals("/user/login") && headers.get("method").equals("POST")){
            login(params.get("userId"), params.get("password"), dataOutputStream);
        }

        return ResponseEntity.builder()
                .status(status)
                .cookie(cookie)
                .contentType(contentType)
                .redirectUrl(redirectUrl)
                .body(body)
                .build();
    }

    // Cookie가 없다면, Session에서 찾아서 넣어주기
    private void findCookieInSession(String userId){
        if(Objects.isNull(cookie) || cookie.startsWith("JSESSIONID")) return;
        Session session = findSession(cookie);
        if(!Objects.isNull(session)){
            this.cookie = session.getId();
        }
    }

    private void setRedirectResponse(String redirectUrl){
        this.status = 302;
        this.redirectUrl = redirectUrl;
    }
    private void alreadyLogin() {
        setRedirectResponse("/index.html");
    }

    private void login(String userId, String password, DataOutputStream dataOutputStream) {
        if(isUser(userId, password)){
            loginSuccess(userId);
            return;
        }
        loginFail();
    }

    private void loginSuccess(String userId){
        // 쿠키 생성
        Cookie cookie = new Cookie();
        cookie.generateUUID();
        this.cookie = cookie.toString();
        // 세션 저장
        saveSession(cookie.toString(), userId);
        setRedirectResponse("/index.html");
    }

    private void saveSession(String sessionId, String userId){
        Session session = new Session(sessionId);
        session.setAttribute("user", findUserById(userId));
        add(session);
    }

    private void loginFail(){
        setRedirectResponse("/user/login_failed.html");
    }

    private void loginFailForm(String path){
        setTemplatePathToBody(path);
    }

    // 로그인 검증 로직
    private boolean isUser(String userId, String password){
        User user = findUserById(userId);
        if(Objects.isNull(user) || !password.equals(user.getPassword())) return false;
        return true;
    }

    private void getUserList(String contentType, String cookie, DataOutputStream dataOutputStream){
        // login이 안되어있다면
        if(!cookie.startsWith("JSESSIONID")){
            String path = "/user/login.html";
            loginForm(path);
            return;
        }
        userList();
    }

    private void loginForm(String path){
        if(!cookie.startsWith("JSESSIONID")) {
            setTemplatePathToBody(path);
            return;
        }
        // 로그인이 되어 있다면, index.html로 redirect
        setRedirectResponse("/index.html");
    }

    private void userList() {
        try{
            TemplateLoader loader = new ClassPathTemplateLoader();
            loader.setPrefix("/templates");
            loader.setSuffix(".hbs");
            Handlebars handlebars = new Handlebars(loader);

            Template template = handlebars.compile("user/list");

            // handlebars 모델 전송용 dto
            Users users = new Users(findAll().stream().collect(Collectors.toList()));

            String userList = template.apply(users);
            body = userList.getBytes();
        } catch(IOException e){
            logger.error(e.getMessage());
        }
    }

    private void createUser(MyParams params){
        // Memory DB에 유저 데이터 저장
        addUser(UserFactory.createUser(params));
        // 회원가입 시 자동 로그인이 되어야 되기 때문에, login 설정
        loginSuccess(params.get("userId"));
    }

    private void createForm(String path){
        setTemplatePathToBody(path);
    }

    private void setTemplatePathToBody(String path){
        try {
            body = FileIoUtils.loadFileFromClasspath("templates" + path);
        } catch (IOException e) {
            logger.error(e.getMessage());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
