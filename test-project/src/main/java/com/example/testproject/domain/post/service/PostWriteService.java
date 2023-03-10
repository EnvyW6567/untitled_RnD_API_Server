package com.example.testproject.domain.post.service;

import com.example.testproject.domain.post.dto.PostDTO;
import com.example.testproject.domain.post.dto.PostResponseDTO;
import com.example.testproject.domain.post.entity.Image;
import com.example.testproject.domain.post.entity.PostLikes;
import com.example.testproject.domain.post.entity.Post;
import com.example.testproject.domain.post.entity.Timeline;
import com.example.testproject.domain.post.repository.*;
import com.example.testproject.exception.CustomException;
import com.example.testproject.domain.user.repository.FollowRepository;
import com.example.testproject.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Component
public class PostWriteService {

    final private PostRepository postRepository;
    final private UserRepository userRepository;
    final private FollowRepository followRepository;
    final private TimelineRepository timelineRepository;
    final private ImageRepository imageRepository;
    final private PostLikesRepository likeRepository;
    final private PostCategoryRepository postCategoryRepository;

    @Value("${static.path}")
    private String rootPath;

    @Transactional
    public PostResponseDTO createPost(PostDTO command, List<MultipartFile> images){

        var appUser = userRepository.findById(command.userId()).orElseThrow();
        var postCategory = postCategoryRepository.findByName(command.category());
        var post = Post.builder()
                .appUser(appUser)
                .title(command.title())
                .contents(command.contents())
                .postCategory(postCategory)
                .build();
        var createPost = postRepository.save(post);

        // Timeline PushModel ??????
        /* Timeline PushModel ??????
          ????????? ?????? ????????? ???????????? ?????? ????????????
          timeline entity ?????? ????????? id??? delivery.
          PushModel : write ?????? ?????? ??????.
         */
        var followers = followRepository.findUserIdByFollowId(createPost.getAppUser().getId());
        List<Timeline> timelines = followers.stream().map(f-> Timeline.builder()
                        .postId(createPost.getId())
                        .userId(f)
                .build()).toList();
        timelineRepository.saveAll(timelines);

        // ????????? ????????? ?????? (?????? ?????????)

        Path dir = getPath(rootPath); // ????????? ?????? ???????????? path
        Path url = getPath("localhost:8080/static"); // ????????? URL path
        var newImages = images.stream().map(i-> saveImage(i, url, dir, createPost)).toList();

        return new PostResponseDTO(createPost, newImages);
    }


    @Transactional
    public PostResponseDTO updatePost(PostDTO command, Long postId){

        var post = this.postRepository.findById(postId).orElseThrow();

        if (!Objects.equals(post.getAppUser().getId(), command.userId())){
            throw new CustomException("No Permission to Update", HttpStatus.BAD_REQUEST);
        }
        var updatePost = postRepository.save(Post.builder()
                        .id(post.getId())
                        .appUser(post.getAppUser())
                        .createdDate(post.getCreatedDate())
                        .title(command.title())
                        .contents(command.contents())
                        .createdAt(LocalDateTime.now())
                .build());

        return new PostResponseDTO(updatePost);
    } // TODO updatePost ????????? ?????? ?????? ?????? ??????.... ?????? update ????????? ?????? ??????...

    @Transactional
    public void postLikes(Long userId, Long postId){

        var likes = likeRepository.findByUserIdAndPostId(userId, postId);
        if (likes == null){
            likeRepository.save(PostLikes.builder().userId(userId).postId(postId).build());
        }
        else{
            likeRepository.delete(likes);
        }

    }

    // --------------- ????????? ????????? ?????? ---------------//
    private Image saveImage(MultipartFile image, Path url, Path dir, Post post) {

        System.out.println(image.isEmpty());
        if (image.isEmpty()){
            System.out.println("is Empty");
            return null;
        }
        System.out.println("is not Empty");
        //?????? ???????????? ?????? uuid??? ????????????
        var fileName = image.getOriginalFilename();
        var saveFileName = uuidFileName(fileName);

        try {
            Files.createDirectories(dir); //????????? ????????? ?????? ?????? ?????????. exception ???????????? ??????
            Path targetPath = dir.resolve(saveFileName).normalize(); // ??????????????? ????????? directory ????????? ??????
            String urlPath = url.resolve(saveFileName).normalize().toString(); // ?????? ????????? ????????? url ?????? ??????
            if (Files.exists(targetPath)) {
                throw new CustomException("Failed due to duplicate files.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            image.transferTo(targetPath);
            return imageRepository.save(Image.builder()
                    .post(post)
                    .name(saveFileName)
                    .path(urlPath).build());
        } catch (CustomException | IOException e) {
            e.printStackTrace();
            throw new CustomException("Failed due to duplicate files.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    private Path getPath(String path) {
        Date createDate = new Date();
        var year = (new SimpleDateFormat("yyyy").format(createDate)); //??????
        var month = (new SimpleDateFormat("MM").format(createDate)); //???
        var day = (new SimpleDateFormat("dd").format(createDate)); //???

        return Paths.get(path, year, month, day);
    }
    private String uuidFileName(String originalFileName) {
        UUID uuid = UUID.randomUUID();
        return uuid.toString() + '_' + originalFileName;
    }
}
