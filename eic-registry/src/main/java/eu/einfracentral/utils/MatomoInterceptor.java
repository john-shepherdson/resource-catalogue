package eu.einfracentral.utils;

import org.apache.log4j.Logger;
import org.piwik.java.tracking.PiwikRequest;
import org.piwik.java.tracking.PiwikTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;

@Component
public class MatomoInterceptor extends HandlerInterceptorAdapter {

    private static Logger logger = Logger.getLogger(MatomoInterceptor.class);

    @Value("${apitracking.matomo.site}")
    private Integer siteId;

    @Value("${apitracking.matomo.host}")
    private String matomoUrl;

    private PiwikTracker piwikTracker = null;

    @PostConstruct
    public void init() {
        if (matomoUrl != null)
            this.piwikTracker = new PiwikTracker(matomoUrl);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);

        if (piwikTracker != null) {

            if (request.getHeader("Referer") == null) {
                logger.debug("Referer is null. That probably means that the call did not come from the portal. Logging!");

                PiwikRequest piwikRequest = new PiwikRequest(siteId, new URL(request.getRequestURL().toString()));

                piwikRequest.setActionName(request.getRequestURI());
                piwikRequest.setUserId(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
                try {
                    piwikRequest.setReferrerUrl(new URL(request.getHeader("Referer")));
                } catch (Exception e) {
                    logger.error("Error setting referer", e);
                }


                piwikTracker.sendRequestAsync(piwikRequest);
            } else {
                logger.debug("Referer is not null. Ignoring!");
            }
        }
    }
}
