package me.micartey.jation.network.packets;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.micartey.jation.network.serializer.Serialize;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PacketInvokeMethod {

    @Serialize("ackId")
    private int ackId;

    @Serialize("eventClass")
    public String eventClass;

    @Serialize("eventData")
    public String eventData;

}
