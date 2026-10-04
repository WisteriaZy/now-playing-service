import com.alibaba.fastjson.JSON;
import com.widdit.nowplaying.controller.SettingsController;
import com.widdit.nowplaying.entity.SettingsGeneral;
import com.widdit.nowplaying.json.JacksonObjectMapper;
import com.widdit.nowplaying.service.SettingsService;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated controller checks: no listening server, audio subprocess or user's settings. */
public class SettingsApiChecks {
    public static void main(String[] args) throws Exception {
        SettingsService service = mock(SettingsService.class);
        SettingsController controller = new SettingsController();
        ReflectionTestUtils.setField(controller, "settingsService", service);
        JacksonObjectMapper mapper = new JacksonObjectMapper();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        int checks = 0;
        when(service.getSettingsGeneral()).thenReturn(new SettingsGeneral());
        mvc.perform(get("/api/settings/general")).andExpect(status().isOk())
                .andExpect(jsonPath("$.splayerNextPort").value(14558));
        checks++;
        for (int port : new int[]{1, 14558, 46157, 65535}) {
            clearInvocations(service);
            mvc.perform(put("/api/settings/general").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"platform\":\"splayer-next\",\"splayerNextPort\":" + port + "}"))
                    .andExpect(status().isOk());
            ArgumentCaptor<SettingsGeneral> saved = ArgumentCaptor.forClass(SettingsGeneral.class);
            verify(service).updateSettingsGeneral(saved.capture());
            if (saved.getValue().getSplayerNextPort() != port) throw new AssertionError("Port not preserved");
            if (!"splayer-next".equals(saved.getValue().getPlatform())) throw new AssertionError("Platform not preserved");
            checks++;
        }
        for (String value : new String[]{"0", "-1", "65536", "null", "\"invalid\""}) {
            clearInvocations(service);
            mvc.perform(put("/api/settings/general").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"splayerNextPort\":" + value + "}"))
                    .andExpect(status().isBadRequest());
            verify(service, never()).updateSettingsGeneral(any());
            checks++;
        }
        clearInvocations(service);
        mvc.perform(put("/api/settings/general").contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"netease\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<SettingsGeneral> legacy = ArgumentCaptor.forClass(SettingsGeneral.class);
        verify(service).updateSettingsGeneral(legacy.capture());
        if (legacy.getValue().getSplayerNextPort() != 14558) throw new AssertionError("Legacy HTTP settings default");
        checks++;
        if (JSON.parseObject("{\"platform\":\"netease\"}", SettingsGeneral.class).getSplayerNextPort() != 14558)
            throw new AssertionError("Legacy persisted settings default");
        checks++;
        for (String value : new String[]{"0", "-1", "65536", "null", "\"invalid\""}) {
            clearInvocations(service);
            mvc.perform(put("/api/settings/general").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tosuPort\":" + value + "}"))
                    .andExpect(status().isBadRequest());
            verify(service, never()).updateSettingsGeneral(any());
            checks++;
        }
        if (JSON.parseObject("{}", SettingsGeneral.class).getTosuPort() != 24050)
            throw new AssertionError("Legacy tosu default");
        checks++;
        clearInvocations(service);
        mvc.perform(put("/api/settings/general").contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"tosu\",\"tosuPort\":25555}"))
                .andExpect(status().isOk());
        ArgumentCaptor<SettingsGeneral> tosu = ArgumentCaptor.forClass(SettingsGeneral.class);
        verify(service).updateSettingsGeneral(tosu.capture());
        if (tosu.getValue().getTosuPort() != 25555) throw new AssertionError("tosu port lost");
        checks++;
        System.out.println("PASS: " + checks + " settings API and legacy configuration checks");
    }
}
