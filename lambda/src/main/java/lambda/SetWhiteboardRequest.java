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
public class SetWhiteboardRequest {
    private Integer apiVersion;
    private String identifier;
    private Long sourceWhiteboardVersion;
    private String content;
}
