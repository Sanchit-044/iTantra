# High-Priority Emergency Alerts & SOS UI Flow

Screenshots validating the ISRO PS 26173 non-interruptible highest volume alert requirement:

- `01_alert_modal.png`: Quick-dispatch alert trigger drawer with 5 pre-rendered templates and custom broadcast option.
- `02_alert_transmitting.png`: Priority queue preemption (alerts jump to head of FIFO queue in `ChannelArbiter`).
- `03_alert_receiving_siren.png`: Full-screen emergency banner with forced max-volume audio override (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) through device speaker.
- `04_sos_broadcast.png`: High-visibility emergency beacon mode with GPS coordinates and continuous retry beaconing.
