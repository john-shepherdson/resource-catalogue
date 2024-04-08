package gr.uoa.di.madgik.resourcecatalogue.controllers.registry;

import gr.uoa.di.madgik.resourcecatalogue.domain.*;
import gr.uoa.di.madgik.registry.domain.FacetFilter;
import gr.uoa.di.madgik.resourcecatalogue.domain.*;
import gr.uoa.di.madgik.resourcecatalogue.service.*;


import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("pid")
@Tag(name = "pid-controller", description = "Get information about a specific Resource via its PID")
public class PIDController {

    private final PIDService pidService;
    private final ProviderService<ProviderBundle, Authentication> providerService;
    private final ServiceBundleService<ServiceBundle> serviceService;
    private final TrainingResourceService<TrainingResourceBundle> trainingResourceService;
    private final InteroperabilityRecordService<InteroperabilityRecordBundle> interoperabilityRecordService;

    public PIDController(PIDService pidService, ProviderService<ProviderBundle, Authentication> providerService,
                         ServiceBundleService<ServiceBundle> serviceService,
                         TrainingResourceService<TrainingResourceBundle> trainingResourceService,
                         InteroperabilityRecordService<InteroperabilityRecordBundle> interoperabilityRecordService) {
        this.pidService = pidService;
        this.providerService = providerService;
        this.serviceService = serviceService;
        this.trainingResourceService = trainingResourceService;
        this.interoperabilityRecordService = interoperabilityRecordService;
    }

    @Operation(description = "Returns the Resource with the given PID.")
    @GetMapping(path = "{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<?> get(@RequestParam String resourceType, @PathVariable("id") String pid) {
        Bundle<?> bundle = pidService.get(resourceType, pid);
        if (bundle != null) {
            return new ResponseEntity<>(bundle.getPayload(), HttpStatus.OK);
        }
        return new ResponseEntity<>(null, HttpStatus.OK);
    }

    @Parameter(hidden = true)
    @PutMapping(path = "updateAllHandles", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void updateAllHandles(@Parameter(hidden = true) Authentication auth) {
        FacetFilter ff = createFacetFilter();
        List<ProviderBundle> allPublishedProviders = providerService.getAll(ff, auth).getResults();
        for (ProviderBundle providerBundle : allPublishedProviders) {
            List<AlternativeIdentifier> alternativeIdentifiers = providerBundle.getProvider().getAlternativeIdentifiers();
            for (AlternativeIdentifier alternativeIdentifier : alternativeIdentifiers) {
                if (alternativeIdentifier.getType().equalsIgnoreCase("PID") ||
                        alternativeIdentifier.getType().equalsIgnoreCase("EOSC PID")) {
                    pidService.updatePID(alternativeIdentifier.getValue(), providerBundle.getId(), "providers/");
                    break;
                }
            }
        }
        List<ServiceBundle> allPublishedServices = serviceService.getAll(ff, auth).getResults();
        for (ServiceBundle serviceBundle : allPublishedServices) {
            List<AlternativeIdentifier> alternativeIdentifiers = serviceBundle.getService().getAlternativeIdentifiers();
            for (AlternativeIdentifier alternativeIdentifier : alternativeIdentifiers) {
                if (alternativeIdentifier.getType().equalsIgnoreCase("PID") ||
                        alternativeIdentifier.getType().equalsIgnoreCase("EOSC PID")) {
                    pidService.updatePID(alternativeIdentifier.getValue(), serviceBundle.getId(), "services/");
                    break;
                }
            }
        }
        List<TrainingResourceBundle> allPublishedTrainingResources = trainingResourceService.getAll(ff, auth).getResults();
        for (TrainingResourceBundle trainingResourceBundle : allPublishedTrainingResources) {
            List<AlternativeIdentifier> alternativeIdentifiers = trainingResourceBundle.getTrainingResource().getAlternativeIdentifiers();
            for (AlternativeIdentifier alternativeIdentifier : alternativeIdentifiers) {
                if (alternativeIdentifier.getType().equalsIgnoreCase("PID") ||
                        alternativeIdentifier.getType().equalsIgnoreCase("EOSC PID")) {
                    pidService.updatePID(alternativeIdentifier.getValue(), trainingResourceBundle.getId(), "trainings/");
                    break;
                }
            }
        }
        List<InteroperabilityRecordBundle> allInteroperabilityRecords = interoperabilityRecordService.getAll(ff, auth).getResults();
        for (InteroperabilityRecordBundle interoperabilityRecordBundle : allInteroperabilityRecords) {
            List<AlternativeIdentifier> alternativeIdentifiers = interoperabilityRecordBundle.getInteroperabilityRecord().getAlternativeIdentifiers();
            for (AlternativeIdentifier alternativeIdentifier : alternativeIdentifiers) {
                if (alternativeIdentifier.getType().equalsIgnoreCase("PID") ||
                        alternativeIdentifier.getType().equalsIgnoreCase("EOSC PID")) {
                    pidService.updatePID(alternativeIdentifier.getValue(), interoperabilityRecordBundle.getId(), "guidelines/");
                    break;
                }
            }
        }
    }

    private FacetFilter createFacetFilter() {
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        ff.addFilter("published", true);
        return ff;
    }
}
