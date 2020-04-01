package eu.einfracentral.service;

import eu.einfracentral.domain.Provider;
import eu.einfracentral.domain.ProviderBundle;
import eu.einfracentral.domain.Service;
import eu.einfracentral.domain.User;
import eu.einfracentral.exception.ResourceNotFoundException;
import eu.einfracentral.registry.manager.PendingProviderManager;
import eu.einfracentral.registry.manager.ProviderManager;
import eu.openminted.registry.core.domain.FacetFilter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RegistrationMailService {

    private static final Logger logger = LogManager.getLogger(RegistrationMailService.class);
    private MailService mailService;
    private Configuration cfg;
    private ProviderManager providerManager;
    private PendingProviderManager pendingProviderManager;


    @Value("${webapp.homepage}")
    private String endpoint;

    @Value("${project.debug:false}")
    private boolean debug;

    @Value("${project.name:CatRIS}")
    private String projectName;

    @Value("${project.registration.email:registration@catris.eu}")
    private String registrationEmail;

    @Value ("${project.admins}")
    private String projectAdmins;

    final private String provCron = "0 0 12 ? * Sun";
    final private String adminCron = "0 0 12 ? * MON,FRI";


    @Autowired
    public RegistrationMailService(MailService mailService, Configuration cfg,
                                   ProviderManager providerManager, @Lazy PendingProviderManager pendingProviderManager) {
        this.mailService = mailService;
        this.cfg = cfg;
        this.providerManager = providerManager;
        this.pendingProviderManager = pendingProviderManager;
    }

    @Async
    public void sendProviderMails(ProviderBundle providerBundle) {
        Map<String, Object> root = new HashMap<>();
        StringWriter out = new StringWriter();
        String providerMail;
        String regTeamMail;

        String providerSubject = null;
        String regTeamSubject = null;

        String providerName;
        if (providerBundle != null && providerBundle.getProvider() != null) {
            providerName = providerBundle.getProvider().getName();
        } else {
            throw new ResourceNotFoundException("Provider is null");
        }

        List<Service> serviceList = providerManager.getServices(providerBundle.getId());
        Service serviceTemplate = null;
        if (!serviceList.isEmpty()) {
            root.put("service", serviceList.get(0));
            serviceTemplate = serviceList.get(0);
        } else {
            serviceTemplate = new Service();
            serviceTemplate.setName("");
        }
        switch (Provider.States.fromString(providerBundle.getStatus())) {
            case PENDING_1:
                providerSubject = String.format("[%s] Your application for registering [%s] " +
                        "as a new service provider has been received", projectName, providerName);
                regTeamSubject = String.format("[%s] A new application for registering [%s] " +
                        "as a new service provider has been submitted", projectName, providerName);
                break;
            case ST_SUBMISSION:
                providerSubject = String.format("[%s] The information you submitted for the new service provider " +
                        "[%s] has been approved - the submission of a first service is required " +
                        "to complete the registration process", projectName, providerName);
                regTeamSubject = String.format("[%s] The application of [%s] for registering " +
                        "as a new service provider has been accepted", projectName, providerName);
                break;
            case REJECTED:
                providerSubject = String.format("[%s] Your application for registering [%s] " +
                        "as a new service provider has been rejected", projectName, providerName);
                regTeamSubject = String.format("[%s] The application of [%s] for registering " +
                        "as a new service provider has been rejected", projectName, providerName);
                break;
            case PENDING_2:
                assert serviceTemplate != null;
                providerSubject = String.format("[%s] Your service [%s] has been received " +
                        "and its approval is pending", projectName, serviceTemplate.getName());
                regTeamSubject = String.format("[%s] Approve or reject the information about the new service: " +
                        "[%s] – [%s]", projectName, providerBundle.getProvider().getName(), serviceTemplate.getName());
                break;
            case APPROVED:
                if (providerBundle.isActive()) {
                    assert serviceTemplate != null;
                    providerSubject = String.format("[%s] Your service [%s] – [%s]  has been accepted",
                            projectName, providerName, serviceTemplate.getName());
                    regTeamSubject = String.format("[%s] The service [%s] has been accepted",
                            projectName, serviceTemplate.getId());
                    break;
                } else {
                    assert serviceTemplate != null;
                    providerSubject = String.format("[%s] Your service provider [%s] has been set to inactive",
                            projectName, providerName);
                    regTeamSubject = String.format("[%s] The service provider [%s] has been set to inactive",
                            projectName, providerName);
                    break;
                }
            case REJECTED_ST:
                assert serviceTemplate != null;
                providerSubject = String.format("[%s] Your service [%s] – [%s]  has been rejected",
                        projectName, providerName, serviceTemplate.getName());
                regTeamSubject = String.format("[%s] The service [%s] has been rejected",
                        projectName, serviceTemplate.getId());
                break;
        }

        root.put("providerBundle", providerBundle);
        root.put("endpoint", endpoint);
        root.put("project", projectName);
        root.put("registrationEmail", registrationEmail);
        // get the first user's information for the registration team email
        root.put("user", providerBundle.getProvider().getUsers().get(0));

        try {
            Template temp = cfg.getTemplate("registrationTeamMailTemplate.ftl");
            temp.process(root, out);
            regTeamMail = out.getBuffer().toString();
            if (!debug) {
                mailService.sendMail(registrationEmail, regTeamSubject, regTeamMail);
            }
            logger.info("Recipient: {}\nTitle: {}\nMail body: \n{}", registrationEmail,
                    regTeamSubject, regTeamMail);

            temp = cfg.getTemplate("providerMailTemplate.ftl");
            for (User user : providerBundle.getProvider().getUsers()) {
                if (user.getEmail() == null || user.getEmail().equals("")) {
                    continue;
                }
                root.remove("user");
                out.getBuffer().setLength(0);
                root.put("user", user);
                root.put("project", projectName);
                temp.process(root, out);
                providerMail = out.getBuffer().toString();
                if (!debug) {
                    mailService.sendMail(user.getEmail(), providerSubject, providerMail);
                }
                logger.info("Recipient: {}\nTitle: {}\nMail body: \n{}", user.getEmail(), providerSubject, providerMail);
            }

            out.close();
        } catch (IOException e) {
            logger.error("Error finding mail template", e);
        } catch (TemplateException e) {
            logger.error("ERROR", e);
        } catch (MessagingException e) {
            logger.error("Could not send mail", e);
        }
    }

//    @Scheduled(cron = provCron)
    @Scheduled(initialDelay = 0, fixedRate = 60000)
    public void sendEmailNotificationsToProviders(){
        List<ProviderBundle> activeProviders = providerManager.getAllActiveForScheduler().getResults();
        List<ProviderBundle> pendingProviders = pendingProviderManager.getAllPendingForScheduler().getResults();
        List<ProviderBundle> allProviders = Stream.concat(activeProviders.stream(), pendingProviders.stream()).collect(Collectors.toList());
        String to;
        for (ProviderBundle providerBundle : allProviders){
            if (providerBundle.getStatus().equals(Provider.States.ST_SUBMISSION.getKey())){
                if (providerBundle.getProvider().getUsers() != null && !providerBundle.getProvider().getUsers().isEmpty()){
                    to = providerBundle.getProvider().getUsers().get(0).getEmail();
                } else{
                    continue;
                }
                String subject = String.format("[%s] Friendly reminder for your Provider [%s]", projectName, providerBundle.getProvider().getName());
                String text = String.format("We kindly remind you to conclude with the submission of the Service Template for your Provider [%s].", providerBundle.getProvider().getName())
                        + "\nYou can view your Provider here: " +endpoint+"/myServiceProviders"
                        + "\n\nBest Regards, \nThe CatRIS Team";
                try{
                    if (!debug){
                        mailService.sendMail(to, subject, text);
                        logger.info("Recipient: {}\nTitle: {}\nMail body: \n{}", to, subject, text);
                    }
                } catch (MessagingException e) {
                    logger.error("Could not send mail", e);
                }
            }
        }
    }

//    @Scheduled(cron = adminCron)
    @Scheduled(initialDelay = 0, fixedRate = 60000)
    public void sendEmailNotificationsToAdmins(){
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        List<ProviderBundle> allProviders = providerManager.getAll(ff, null).getResults();
        String[] admins = projectAdmins.split(",");
        List<String> providerNamesWaitingForApproval = new ArrayList<>();
        for (ProviderBundle providerBundle : allProviders) {
            if (providerBundle.getStatus().equals(Provider.States.PENDING_1.getKey()) || providerBundle.getStatus().equals(Provider.States.PENDING_2.getKey())) {
                providerNamesWaitingForApproval.add(providerBundle.getProvider().getName());
            }
        }
        if (!providerNamesWaitingForApproval.isEmpty()){
            for (int i=0; i<admins.length; i++){
                String to = admins[i];
                String subject = String.format("[%s] Some new Providers are pending for your approval", projectName);
                String text = "There are Providers and Service Templates waiting to be approved: \n" + providerNamesWaitingForApproval
                        + "\nYou can review them at: " +endpoint+"/serviceProvidersList"
                        + "\n\nBest Regards, \nThe CatRIS Team";
                try{
                    if (!debug){
                        mailService.sendMail(to, subject, text);
                        logger.info("Recipient: {}\nTitle: {}\nMail body: \n{}", to, subject, text);
                    }
                } catch (MessagingException e) {
                    logger.error("Could not send mail", e);
                }
            }
        }
    }
}
