package io.quarkiverse.dashbuilder.deployment.devui;

import io.quarkiverse.dashbuilder.deployment.DashbuilderConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class DashbuilderDevUiProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(DashbuilderConfig dashbuilderConfig) {

        var cardPageBuildItem = new CardPageBuildItem();

        cardPageBuildItem.addPage(Page.externalPageBuilder("Dashboards")
                .icon("font-awesome-solid:chart-simple")
                .internal()
                .url(dashbuilderConfig.path())
                .title("Dashboards"));

        cardPageBuildItem.addPage(Page.externalPageBuilder("Samples")
                .icon("font-awesome-solid:chart-bar")
                .internal()
                .url(dashbuilderConfig.path() + "/?samples")
                .title("Samples"));

        return cardPageBuildItem;

    }

}
