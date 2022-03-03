# ClipZoomFx
Side-project for extracting highlights from videos. It started as way to capture exceptional 
plays from volleyball matches I've recorded. This may use high 
quality video footage that accessible by the computer it is running
on.

ClipZoomFx keeps a library of video files that can be played
back in the application. "Highlight-able" portions (clips) can be designated.
A highlight reel can be generated by selecting clips and
having them concatenated using a library that uses ffmpeg.

Clips can also be zoomed in, and it uses JavaFX, hence the
name ClipZoomFx (I didn't spend that much time thinking
about the name ;))

###Near Term Fixes and Enhancements

- Ability re-order clips has associated with the video
file and during generation of concatenated highlight video
- Ability to rename clips other than the timestamp offset 
in the video file.
- Create a back-up of any project file being overwritten.
- If a video file is found to be missing allow the use 
locate it and not lose associated clips.
- Some icons need to updated to match better the actions
they represent.

#### Made Possible by Libraries
- https://github.com/caprica/vlcj
- https://github.com/kokorin/Jaffree
- https://github.com/controlsfx/controlsfx