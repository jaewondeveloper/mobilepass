얼굴 비교(본인 확인)용 임베딩 모델.

파일: face_recognition_sface.onnx  (OpenCV Zoo 의 SFace 2021dec, int8 양자화본, 약 9.9MB, Apache-2.0)
  출처: https://huggingface.co/opencv/face_recognition_sface  (face_recognition_sface_2021dec_int8.onnx)
  - 입력  : [1, 3, 112, 112] float32, RGB, 0..255 (정규화는 모델 안에 포함), 정렬된 얼굴
  - 출력  : [1, 128] 임베딩 (코사인 유사도로 비교, 기준값 0.363)
저사양 기기를 위해 크기가 작고 빠른 양자화본을 쓴다. 더 정확한 원본(약 37MB)으로 바꾸려면 같은 이름으로 교체하면 된다.
