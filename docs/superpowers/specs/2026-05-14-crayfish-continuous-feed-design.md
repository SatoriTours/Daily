# Crayfish Continuous Feed Design

## Goal

Replace the Crayfish News list/detail interaction with a continuous reading feed that shows full articles inline and loads older articles as the user scrolls.

## Behavior

- General news requests `/news?category=general&limit=50`.
- DJI news requests `/news?category=dji&limit=50`.
- The feed initially loads 3 full articles from the returned file list.
- When the user reaches the end, the feed loads 3 more full articles.
- Each article fetches full content through `/news/general/{filename}` or `/news/dji/{filename}`.
- Each article renders cleaned markdown `content` inline.
- The UI no longer uses a separate detail screen for Crayfish News.
- The existing menu keeps category switching, refresh, and return to remote news.
