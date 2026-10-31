# EventOps: 75-second walkthrough

[Watch the captioned video](eventops-demo.mp4) · [Live application](https://eventops-mbekele1222.onrender.com/)

The video is a silent, captioned walkthrough assembled from real application screenshots. It combines a live signup/cancellation sequence with the organizer dashboard captured during the October 30 QA run. Every attendee shown is synthetic. Private management codes are cropped out. It is not a continuous screen recording.

## Narration for an in-person demo

| Time | Screen | Suggested narration |
|---|---|---|
| 0:00–0:05 | Public events | “I built EventOps to handle event registration, capacity, waitlists, and attendance.” |
| 0:05–0:13 | Signup form | “Attendees choose an event and register with their name and email. They don't need an account.” |
| 0:13–0:20 | Confirmed receipt | “Alex takes the last available seat. The receipt confirms the registration and provides a private management code.” |
| 0:20–0:28 | Waitlisted receipt | “When Bri registers next, the event is full, so the application places Bri on its waitlist.” |
| 0:28–0:35 | Private status | “Attendees use their saved code to check their status or manage the registration.” |
| 0:35–0:44 | Cancellation confirmation | “When a confirmed attendee cancels, a database transaction releases the seat and promotes the next person in line.” |
| 0:44–0:52 | Promoted registration #11 | “Bri's same registration is now confirmed. Event-row locks protect capacity and promotion when requests arrive together.” |
| 0:52–1:01 | Organizer dashboard | “Organizers see SQL summaries for capacity, waitlists, cancellations, and attendance, plus search and status filters.” |
| 1:01–1:08 | Persisted check-in | “Organizers can check in confirmed attendees. I verified that check-in persists through refresh and page reload.” |
| 1:08–1:15 | Stack and testing | “The stack is Java, Spring Boot, PostgreSQL, and JavaScript. It has 29 automated tests and runs on Render using Docker.” |

## Capture notes

- The signup sequence uses the two-seat **Oct 30 Automated QA** event. An earlier checked-in QA attendee already occupied one seat.
- Alex Demo received registration **#10**, confirmed. Bri Demo received **#11**, waitlisted. Cancelling #10 promoted #11 to confirmed.
- Both demo registrations were cancelled after capture. Cancellation history is retained by the application.
- The dashboard screenshot shows the earlier verified QA state: registrations #7 and #8 cancelled, #9 confirmed and checked in. It is a separate checkpoint from the signup sequence.
- The dashboard screenshot predates the singular-seat wording fix in this polish update.

## Rebuild the video

Install FFmpeg and DejaVu Sans fonts, then run:

```bash
python3 scripts/build_demo_video.py
```

Pass `--font-dir /path/to/fonts` if the DejaVu Sans fonts are installed elsewhere. The generated MP4 is 1920 × 1080, 24 frames per second, H.264, with no audio track.
