package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.enrichment.preferences.CustomIconResolver;
import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkListServiceTest {
    private static final CustomIconResolver ICONS =
            new CustomIconResolver(new IconSettings("https://cdn.example.com/icons", ".svg"));

    @Test
    void listsOnlyLinksInSavedOrderWithResolvedIcons() {
        LinkRef router = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
        LinkRef nas = new LinkRef("0".repeat(32));
        String upload = "a".repeat(64) + ".png";
        EntryService entries = mock(EntryService.class);
        when(entries.state())
                .thenReturn(new EntryState(List.of(
                        new EntryRecord(
                                router,
                                "Router",
                                new Icon.Upload(upload),
                                new EndpointChoice.Custom("http://r/"),
                                "base"),
                        new EntryRecord(new ContainerRef("walker", "app"), "App", null, null, null),
                        new EntryRecord(nas, "NAS", null, new EndpointChoice.Custom("http://nas/"), null))));

        assertThat(new LinkListService(entries, ICONS).list())
                .containsExactly(
                        new PresentedLink(router.id(), "Router", "http://r/", "base", "/api/icons/" + upload),
                        new PresentedLink(nas.id(), "NAS", "http://nas/", null, null));
    }
}
