package lambda;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SetWhiteboardResponse {
    private String identifier;
    private String content;
    private Long requestSourceWhiteboardVersion;
    private Long existingNewestWhiteboardVersion;
    private Long currentNewestWhiteboardVersion;
}
