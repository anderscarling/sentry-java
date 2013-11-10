package net.kencochrane.raven.event;

import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.Date;
import java.util.UUID;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class EventBuilderTest2 {
    private static void resetHostnameCache() {
        setField(getField(EventBuilder.class, "HOSTNAME_CACHE"), "expirationTimestamp", 0l);
    }

    @Test
    public void builtEventHasRandomlyGeneratedUuid(@Injectable final UUID mockUuid)
            throws Exception {
        new NonStrictExpectations(UUID.class) {{
            UUID.randomUUID();
            result = mockUuid;
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(sameInstance(mockUuid)));
    }

    @Test
    public void builtEventWithCustomUuidHasProperUuid(@Injectable final UUID mockUuid)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder(mockUuid);

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(sameInstance(mockUuid)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void builtEventWithCustomNullUuidFails() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder(null);
    }

    @Test
    public void builtEventWithoutMessageHasNullMessage() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getMessage(), is(nullValue()));
    }

    @Test
    public void builtEventWithMessageHasProperMessage(
            @Injectable("message") final String mockMessage)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setMessage(mockMessage);

        final Event event = eventBuilder.build();

        assertThat(event.getMessage(), is(sameInstance(mockMessage)));
    }

    @Test
    public void builtEventWithoutTimestampHasDefaultTimestamp(@Injectable final Date mockTimestamp)
            throws Exception {
        new NonStrictExpectations(Date.class) {{
            new Date();
            result = mockTimestamp;
            mockTimestamp.clone();
            result = mockTimestamp;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setTimestamp(mockTimestamp);

        final Event event = eventBuilder.build();

        assertThat(event.getTimestamp(), is(sameInstance(mockTimestamp)));
    }

    @Test
    public void builtEventWithTimestampHasProperTimestamp(@Injectable final Date mockTimestamp)
            throws Exception {
        new NonStrictExpectations() {{
            mockTimestamp.clone();
            result = mockTimestamp;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setTimestamp(mockTimestamp);

        final Event event = eventBuilder.build();

        assertThat(event.getTimestamp(), is(sameInstance(mockTimestamp)));
    }

    @Test
    public void builtEventWithoutLevelHasNullLevel() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getLevel(), is(nullValue()));
    }

    @Test
    public void builtEventWithLevelHasProperLevel(@Injectable final Event.Level mockLevel)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setLevel(mockLevel);

        final Event event = eventBuilder.build();

        assertThat(event.getLevel(), is(sameInstance(mockLevel)));
    }

    @Test
    public void builtEventWithoutLoggerHasNullLogger() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getLogger(), is(nullValue()));
    }

    @Test
    public void builtEventWithLoggerHasProperLogger(@Injectable("logger") final String mockLogger)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setLogger(mockLogger);

        final Event event = eventBuilder.build();

        assertThat(event.getLogger(), is(sameInstance(mockLogger)));
    }

    @Test
    public void builtEventWithoutPlatformHasDefaultPlatform() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getPlatform(), is(EventBuilder.DEFAULT_PLATFORM));
    }

    @Test
    public void builtEventWithPlatformHasProperPlatform(@Injectable("platform") final String mockPlatform)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setPlatform(mockPlatform);

        final Event event = eventBuilder.build();

        assertThat(event.getPlatform(), is(sameInstance(mockPlatform)));
    }

    @Test
    public void builtEventWithoutCulpritHasNullCulprit() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(nullValue()));
    }

    @Test
    public void builtEventWithCulpritHasProperCulprit(@Injectable("culprit") final String mockCulprit)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setCulprit(mockCulprit);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(sameInstance(mockCulprit)));
    }

    @DataProvider
    public Object[][] stackFrameProvider() {
        return new Object[][]{
                {new StackTraceElement("class", "method", "file", 12), "class.method(file:12)"},
                {new StackTraceElement("class", "method", "file", -1), "class.method(file)"},
                {new StackTraceElement("class", "method", null, 12), "class.method"},
                {new StackTraceElement("class", "method", null, -1), "class.method"}
        };
    }

    @Test(dataProvider = "stackFrameProvider")
    public void builtEventWithStackFrameAsCulpritHasProperCulprit(StackTraceElement mockStackFrame,
                                                                  String expectedCulprit)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setCulprit(mockStackFrame);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(expectedCulprit));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void builtEventHasImmutableTags() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        final Event event = eventBuilder.build();

        event.getTags().put("tagKey", "tagValue");
    }

    @Test
    public void builtEventWithoutTagsHasEmptyTags() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getTags().entrySet(), is(empty()));
    }

    @Test
    public void builtEventWithTagsHasProperTags(@Injectable("tagKey") final String mockTagKey,
                                                @Injectable("tagValue") final String mockTagValue)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.addTag(mockTagKey, mockTagValue);

        final Event event = eventBuilder.build();

        assertThat(event.getTags(), hasEntry(mockTagKey, mockTagValue));
        assertThat(event.getTags().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventWithNoServerNameUsesDefaultIfSearchTimesOut()
            throws Exception {
        new NonStrictExpectations(InetAddress.class) {
            @Injectable
            private InetAddress mockTimingOutLocalHost;

            {
                InetAddress.getLocalHost();
                result = mockTimingOutLocalHost;
                mockTimingOutLocalHost.getCanonicalHostName();
                result = new Delegate() {
                    public String getCanonicalHostName() throws Exception {
                        synchronized (EventBuilderTest2.this) {
                            EventBuilderTest2.this.wait();
                        }
                        return "";
                    }
                };
            }
        };
        final EventBuilder eventBuilder = new EventBuilder();

        resetHostnameCache();
        final Event event = eventBuilder.build();
        synchronized (this) {
            this.notify();
        }

        assertThat(event.getServerName(), is(EventBuilder.DEFAULT_HOSTNAME));
    }

    @Test
    public void builtEventWithNoServerNameUsesLocalHost(@Injectable("serverName") final String mockServerName)
            throws Exception {
        new NonStrictExpectations(InetAddress.class) {
            @Injectable
            private InetAddress mockLocalHost;

            {
                InetAddress.getLocalHost();
                result = mockLocalHost;
                mockLocalHost.getCanonicalHostName();
                result = mockServerName;
            }
        };
        final EventBuilder eventBuilder = new EventBuilder();

        resetHostnameCache();
        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(mockServerName));
    }

    @Test
    public void builtEventWithServerNameUsesProvidedServerName(@Injectable("serverName") final String mockServerName)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setServerName(mockServerName);

        resetHostnameCache();
        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(mockServerName));
    }
}
