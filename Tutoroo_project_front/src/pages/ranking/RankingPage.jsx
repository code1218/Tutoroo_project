/** @jsxImportSource @emotion/react */
import { useState, useEffect } from "react";
import Header from "../../components/layouts/Header";
import { rankingApi } from "../../apis/ranking/rankingApi";
import RankingFilters from "./RankingFilters";
import RankingList from "./RankingList";
import MyRankingCard from "./MyRankingCard";
import * as s from "./styles";

function RankingPage() {
  const [rankingList, setRankingList] = useState([]);
  const [myRanking, setMyRanking] = useState(null);
  
  const [filterGender, setFilterGender] = useState("전체");
  const [filterAge, setFilterAge] = useState("전체");
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const data = await rankingApi.getRankings(filterGender, filterAge);
        if (data) {
          setRankingList(data.allRankers || []);

          if (data.myRank) {
            setMyRanking(data.myRank);
          } 
          else {
            try {
              const [profile, dashboard] = await Promise.all([
                rankingApi.getMyProfile(),
                rankingApi.getMyDashboard()
              ]);              
              let masked = profile.name;
              if (masked && masked.length >= 2) {
                masked = masked.charAt(0) + "*" + masked.substring(2);
              }
              setMyRanking({
                rank: dashboard.rank,               
                maskedName: masked,   
                name: profile.name,   
                totalPoint: dashboard.currentPoint, 
                profileImage: profile.profileImage  
              });
            } catch (e) {
              setMyRanking(null);
            }
          }
        }
      } catch (error) {
        console.error("랭킹 데이터 로드 실패:", error);
        setRankingList([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [filterGender, filterAge]);

  return (
    <>
      <Header />
      <div css={s.pageBg}>
        <div css={s.container}>
          <RankingFilters
            filterGender={filterGender}
            setFilterGender={setFilterGender}
            filterAge={filterAge}
            setFilterAge={setFilterAge}
          />
          <div css={s.contentWrap}>
            <RankingList 
              rankingList={rankingList} 
              isLoading={isLoading} 
            />
            <MyRankingCard myRanking={myRanking} />
          </div>
        </div>
      </div>
    </>
  );
}

export default RankingPage;